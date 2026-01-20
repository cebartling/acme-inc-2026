package com.acme.identity.infrastructure.ratelimit

import com.acme.identity.domain.SmsRateLimit
import com.acme.identity.infrastructure.persistence.SmsRateLimitRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Result of checking SMS rate limits.
 */
sealed interface SmsRateLimitResult {
    /**
     * Request is allowed within rate limits.
     *
     * @property remaining Number of requests remaining in the window.
     */
    data class Allowed(val remaining: Int) : SmsRateLimitResult

    /**
     * Request exceeds rate limits.
     *
     * @property retryAfterSeconds Seconds until the rate limit resets.
     */
    data class Exceeded(val retryAfterSeconds: Long) : SmsRateLimitResult
}

/**
 * Result of checking resend cooldown.
 */
sealed interface ResendCooldownResult {
    /**
     * Resend is allowed (cooldown has elapsed).
     */
    data object Allowed : ResendCooldownResult

    /**
     * Resend is blocked by cooldown.
     *
     * @property waitSeconds Seconds to wait before resend is allowed.
     */
    data class Blocked(val waitSeconds: Long) : ResendCooldownResult
}

/**
 * Service for SMS rate limiting.
 *
 * Enforces two types of rate limits:
 * 1. Hourly limit: Max SMS per hour per user (default: 3)
 * 2. Resend cooldown: Minimum time between resends (default: 60 seconds)
 */
@Service
class SmsRateLimiter(
    private val smsRateLimitRepository: SmsRateLimitRepository,

    @Value("\${identity.sms.rate-limiting.max-sms-per-hour:3}")
    private val maxSmsPerHour: Int,

    @Value("\${identity.sms.rate-limiting.resend-cooldown-seconds:60}")
    private val resendCooldownSeconds: Long
) {
    /**
     * Checks if sending an SMS to the user is within rate limits.
     *
     * @param userId The user ID to check.
     * @return Allowed with remaining count, or Exceeded with retry time.
     */
    fun checkRateLimit(userId: UUID): SmsRateLimitResult {
        val oneHourAgo = Instant.now().minus(Duration.ofHours(1))
        val sentCount = smsRateLimitRepository.countByUserIdSince(userId, oneHourAgo)

        return if (sentCount < maxSmsPerHour) {
            SmsRateLimitResult.Allowed(remaining = maxSmsPerHour - sentCount.toInt() - 1)
        } else {
            // Find when the oldest message in the window expires
            // This is approximate - we'd need the actual timestamps for precision
            SmsRateLimitResult.Exceeded(retryAfterSeconds = 3600) // Worst case: 1 hour
        }
    }

    /**
     * Checks if enough time has passed since the last SMS was sent (resend cooldown).
     *
     * @param userId The user ID to check.
     * @return Allowed if cooldown has elapsed, Blocked with wait time otherwise.
     */
    fun checkResendCooldown(userId: UUID): ResendCooldownResult {
        val lastSentAt = smsRateLimitRepository.findMostRecentSentAt(userId)
            ?: return ResendCooldownResult.Allowed

        val secondsSinceLastSend = Duration.between(lastSentAt, Instant.now()).seconds

        return if (secondsSinceLastSend >= resendCooldownSeconds) {
            ResendCooldownResult.Allowed
        } else {
            ResendCooldownResult.Blocked(waitSeconds = resendCooldownSeconds - secondsSinceLastSend)
        }
    }

    /**
     * Records that an SMS was sent to the user.
     *
     * @param userId The user ID.
     * @param phoneNumber The phone number the SMS was sent to.
     */
    @Transactional
    fun recordSmsSent(userId: UUID, phoneNumber: String) {
        val record = SmsRateLimit.create(userId, phoneNumber)
        smsRateLimitRepository.save(record)
    }

    /**
     * Gets the current rate limit info for a user.
     *
     * @param userId The user ID.
     * @return Pair of (remaining SMS count, max SMS per hour).
     */
    fun getRateLimitInfo(userId: UUID): Pair<Int, Int> {
        val oneHourAgo = Instant.now().minus(Duration.ofHours(1))
        val sentCount = smsRateLimitRepository.countByUserIdSince(userId, oneHourAgo)
        val remaining = maxOf(0, maxSmsPerHour - sentCount.toInt())
        return remaining to maxSmsPerHour
    }

    /**
     * Gets seconds until resend is available.
     *
     * @param userId The user ID.
     * @return Seconds until resend is allowed (0 if already allowed).
     */
    fun getResendAvailableIn(userId: UUID): Long {
        return when (val result = checkResendCooldown(userId)) {
            is ResendCooldownResult.Allowed -> 0
            is ResendCooldownResult.Blocked -> result.waitSeconds
        }
    }

    /**
     * Cleans up old rate limit records.
     * Records older than 1 hour are no longer needed for rate limiting.
     *
     * @return Number of records deleted.
     */
    @Transactional
    fun cleanup(): Int {
        val oneHourAgo = Instant.now().minus(Duration.ofHours(1))
        return smsRateLimitRepository.deleteOlderThan(oneHourAgo)
    }
}
