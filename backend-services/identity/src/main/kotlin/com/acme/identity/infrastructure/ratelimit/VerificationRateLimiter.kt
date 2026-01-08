package com.acme.identity.infrastructure.ratelimit

import com.acme.identity.infrastructure.persistence.ResendRequestRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Sealed class representing the result of a rate limit check.
 */
sealed class RateLimitResult {
    /**
     * The request is allowed (under rate limit).
     *
     * @property remaining Number of requests remaining in the time window.
     */
    data class Allowed(val remaining: Int) : RateLimitResult()

    /**
     * The request is rate limited (exceeded limit).
     *
     * @property remaining Number of requests remaining (always 0).
     * @property retryAfter Timestamp when the rate limit will reset.
     */
    data class Exceeded(val remaining: Int = 0, val retryAfter: Instant) : RateLimitResult()
}

/**
 * Rate limiter for verification email resend requests.
 *
 * Implements persistent rate limiting using database storage to track
 * resend requests. This ensures rate limits are enforced across service
 * restarts and multiple instances.
 *
 * Rate limit: 3 requests per hour per email address.
 * Rate limiting can be disabled via configuration for testing purposes.
 *
 * @property resendRepository Repository for persisting rate limit data.
 * @property enabled Whether rate limiting is enabled. Defaults to true.
 * @property maxRequestsPerHour Maximum requests allowed per hour.
 */
@Service
class VerificationRateLimiter(
    private val resendRepository: ResendRequestRepository,
    @Value("\${identity.rate-limiting.enabled:true}")
    private val enabled: Boolean = true,
    @Value("\${identity.rate-limiting.resend.requests-per-hour:3}")
    private val maxRequestsPerHour: Int = 3
) {
    companion object {
        /** Default maximum requests per hour. */
        const val DEFAULT_MAX_REQUESTS_PER_HOUR = 3
    }

    /**
     * Checks the rate limit for a given email address.
     *
     * If rate limiting is disabled, always returns [RateLimitResult.Allowed].
     * Does not consume a request slot; recording of actual requests is handled elsewhere.
     *
     * @param email The email address to check.
     * @return [RateLimitResult] indicating whether the request is allowed.
     */
    fun checkRateLimit(email: String): RateLimitResult {
        if (!enabled) {
            return RateLimitResult.Allowed(remaining = maxRequestsPerHour)
        }

        val oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS)
        val recentRequests = resendRepository.countByEmailSince(email.lowercase(), oneHourAgo)

        return if (recentRequests >= maxRequestsPerHour) {
            val oldestRequest = resendRepository.findOldestByEmailSince(email.lowercase(), oneHourAgo)
            val retryAfter = oldestRequest?.requestedAt?.plus(1, ChronoUnit.HOURS)
                ?: Instant.now().plus(1, ChronoUnit.HOURS)
            RateLimitResult.Exceeded(
                remaining = 0,
                retryAfter = retryAfter
            )
        } else {
            RateLimitResult.Allowed(
                remaining = maxRequestsPerHour - recentRequests.toInt()
            )
        }
    }

    /**
     * Gets information about the current rate limit status for an email.
     *
     * @param email The email address to check.
     * @return A pair of (requests remaining, max requests per hour).
     */
    fun getRateLimitInfo(email: String): Pair<Int, Int> {
        val oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS)
        val recentRequests = resendRepository.countByEmailSince(email.lowercase(), oneHourAgo)
        val remaining = (maxRequestsPerHour - recentRequests).coerceAtLeast(0).toInt()
        return Pair(remaining, maxRequestsPerHour)
    }
}
