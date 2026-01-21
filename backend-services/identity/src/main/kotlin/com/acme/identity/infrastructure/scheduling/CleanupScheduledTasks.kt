package com.acme.identity.infrastructure.scheduling

import com.acme.identity.infrastructure.persistence.MfaChallengeRepository
import com.acme.identity.infrastructure.persistence.UsedTotpCodeRepository
import com.acme.identity.infrastructure.ratelimit.SmsRateLimiter
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Scheduled tasks for cleaning up expired data.
 *
 * Runs periodically to remove:
 * - Expired MFA challenges
 * - Used TOTP codes past their retention window
 * - Old SMS rate limit records (older than 1 hour)
 */
@Component
class CleanupScheduledTasks(
    private val mfaChallengeRepository: MfaChallengeRepository,
    private val usedTotpCodeRepository: UsedTotpCodeRepository,
    private val smsRateLimiter: SmsRateLimiter
) {
    private val logger = LoggerFactory.getLogger(CleanupScheduledTasks::class.java)

    /**
     * Cleans up expired MFA challenges.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedRate = 300_000) // 5 minutes
    @Transactional
    fun cleanupExpiredMfaChallenges() {
        val now = Instant.now()
        val deleted = mfaChallengeRepository.deleteExpiredChallenges(now)
        if (deleted > 0) {
            logger.info("Cleaned up {} expired MFA challenges", deleted)
        }
    }

    /**
     * Cleans up expired used TOTP codes.
     * Runs every 10 minutes.
     */
    @Scheduled(fixedRate = 600_000) // 10 minutes
    @Transactional
    fun cleanupExpiredTotpCodes() {
        val now = Instant.now()
        val deleted = usedTotpCodeRepository.deleteExpiredCodes(now)
        if (deleted > 0) {
            logger.info("Cleaned up {} expired used TOTP codes", deleted)
        }
    }

    /**
     * Cleans up old SMS rate limit records.
     * Records older than 1 hour are no longer needed for rate limiting.
     * Runs every hour.
     */
    @Scheduled(fixedRate = 3_600_000) // 1 hour
    fun cleanupSmsRateLimits() {
        val deleted = smsRateLimiter.cleanup()
        if (deleted > 0) {
            logger.info("Cleaned up {} old SMS rate limit records", deleted)
        }
    }
}
