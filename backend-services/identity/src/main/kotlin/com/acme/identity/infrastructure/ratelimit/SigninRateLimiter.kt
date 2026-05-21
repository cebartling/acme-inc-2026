package com.acme.identity.infrastructure.ratelimit

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID

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

    /**
     * Atomic sliding-window check executed as a single Redis Lua script.
     * Prune → add → expire → count → oldest happen under one keyspace lock,
     * preventing the race that would otherwise let concurrent callers exceed
     * the configured limit.
     *
     * KEYS[1] = sorted-set key
     * ARGV[1] = now (ms), ARGV[2] = windowStart (ms),
     * ARGV[3] = windowSeconds, ARGV[4] = unique member
     * Returns: { count, oldestScoreMs }
     */
    @Suppress("UNCHECKED_CAST")
    private val checkScript: DefaultRedisScript<List<*>> = DefaultRedisScript(
        """
        redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[2])
        redis.call('ZADD', KEYS[1], ARGV[1], ARGV[4])
        redis.call('EXPIRE', KEYS[1], ARGV[3])
        local count = redis.call('ZCARD', KEYS[1])
        local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
        local oldestScore = ARGV[1]
        if oldest[2] then oldestScore = oldest[2] end
        return { tostring(count), tostring(oldestScore) }
        """.trimIndent(),
        List::class.java as Class<List<*>>
    )

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
        val member = "$nowMs:${UUID.randomUUID()}"

        val result = redisTemplate.execute(
            checkScript,
            listOf(key),
            nowMs.toString(),
            windowStartMs.toString(),
            windowSeconds.toString(),
            member
        )
        val count = (result?.getOrNull(0) as? String)?.toIntOrNull() ?: 0
        val oldestMs = (result?.getOrNull(1) as? String)?.toLongOrNull() ?: nowMs

        if (count > limit) {
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

        return SigninRateLimitResult.Allowed(
            limit = limit,
            remaining = (limit - count).coerceAtLeast(0),
            resetAt = Instant.ofEpochMilli(oldestMs + windowSeconds * 1000)
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
