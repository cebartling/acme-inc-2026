package com.acme.identity.infrastructure.ratelimit

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Redis-backed sliding-window rate limiter for the customer signin endpoint.
 *
 * Enforces two independent scopes per US-0003-03:
 *
 *  - IP-based limit (default: 10 / 60s) using Redis key `rate:signin:ip:{ip}`
 *  - Email-based limit (default: 5 / 60s) using Redis key
 *    `rate:signin:email:{sha256(email)[:16]}`
 *
 * The sliding window is implemented with a Redis sorted set per key:
 * each request adds an entry scored by the request time, then entries
 * older than the window are pruned, and the cardinality is the current
 * count. This is true sliding-window behavior (AC-03), not a fixed
 * minute-boundary bucket.
 *
 * Fail-open semantics: any Redis exception is caught and logged, and
 * the request is allowed through (AC-09). Rate limiting can be globally
 * disabled via `identity.rate-limiting.enabled=false` (AC-10).
 */
@Component
class SigninRateLimiter(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${identity.rate-limiting.enabled:true}")
    private val enabled: Boolean = true,
    @Value("\${identity.rate-limiting.signin.ip.limit:10}")
    private val ipLimit: Int = 10,
    @Value("\${identity.rate-limiting.signin.ip.window-seconds:60}")
    private val ipWindowSeconds: Long = 60,
    @Value("\${identity.rate-limiting.signin.email.limit:5}")
    private val emailLimit: Int = 5,
    @Value("\${identity.rate-limiting.signin.email.window-seconds:60}")
    private val emailWindowSeconds: Long = 60,
    private val clock: Clock = Clock.systemUTC()
) {
    private val logger = LoggerFactory.getLogger(SigninRateLimiter::class.java)

    fun checkLimit(ipAddress: String, email: String): SigninRateLimitResult {
        val now = Instant.now(clock)
        if (!enabled) {
            return SigninRateLimitResult.Allowed(
                limit = emailLimit,
                remaining = emailLimit,
                resetAt = now.plusSeconds(emailWindowSeconds)
            )
        }

        return try {
            val ipResult = checkScope(
                key = "rate:signin:ip:$ipAddress",
                limit = ipLimit,
                windowSeconds = ipWindowSeconds,
                scope = SigninRateLimitResult.Scope.IP,
                now = now
            )
            if (ipResult is SigninRateLimitResult.Limited) {
                return ipResult
            }

            val emailResult = checkScope(
                key = "rate:signin:email:${hashEmail(email)}",
                limit = emailLimit,
                windowSeconds = emailWindowSeconds,
                scope = SigninRateLimitResult.Scope.EMAIL,
                now = now
            )
            if (emailResult is SigninRateLimitResult.Limited) {
                return emailResult
            }

            // Both scopes allowed. Surface whichever has fewer remaining so
            // clients self-pace conservatively. Ties broken on earlier resetAt.
            mostRestrictive(
                ipResult as SigninRateLimitResult.Allowed,
                emailResult as SigninRateLimitResult.Allowed
            )
        } catch (e: Exception) {
            logger.error(
                "Rate limit check failed for ip={} emailHash={}; failing open",
                ipAddress, hashEmail(email), e
            )
            SigninRateLimitResult.Allowed(
                limit = emailLimit,
                remaining = emailLimit,
                resetAt = now.plusSeconds(emailWindowSeconds)
            )
        }
    }

    private fun checkScope(
        key: String,
        limit: Int,
        windowSeconds: Long,
        scope: SigninRateLimitResult.Scope,
        now: Instant
    ): SigninRateLimitResult {
        val nowMs = now.toEpochMilli()
        val windowStartMs = nowMs - windowSeconds * 1000

        val zset = redisTemplate.opsForZSet()
        zset.removeRangeByScore(key, Double.NEGATIVE_INFINITY, windowStartMs.toDouble())
        zset.add(key, "$nowMs:${UUID.randomUUID()}", nowMs.toDouble())
        redisTemplate.expire(key, Duration.ofSeconds(windowSeconds))

        val count = (zset.zCard(key) ?: 0L).toInt()

        if (count > limit) {
            val oldest = zset.rangeWithScores(key, 0, 0)?.firstOrNull()
            val oldestMs = oldest?.score?.toLong() ?: nowMs
            val retryAfterMs = (oldestMs + windowSeconds * 1000) - nowMs
            val retryAfterSeconds = ((retryAfterMs + 999) / 1000).coerceAtLeast(1).toInt()
            return SigninRateLimitResult.Limited(
                limit = limit,
                resetAt = Instant.ofEpochMilli(oldestMs + windowSeconds * 1000),
                retryAfterSeconds = retryAfterSeconds,
                scope = scope,
                currentCount = count
            )
        }

        val ttlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS).takeIf { it > 0 }
            ?: windowSeconds
        return SigninRateLimitResult.Allowed(
            limit = limit,
            remaining = (limit - count).coerceAtLeast(0),
            resetAt = now.plusSeconds(ttlSeconds)
        )
    }

    private fun mostRestrictive(
        a: SigninRateLimitResult.Allowed,
        b: SigninRateLimitResult.Allowed
    ): SigninRateLimitResult.Allowed = when {
        a.remaining != b.remaining -> if (a.remaining < b.remaining) a else b
        else -> if (a.resetAt.isBefore(b.resetAt)) a else b
    }

    private fun hashEmail(email: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(email.lowercase().toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)
}
