package com.acme.identity.infrastructure.persistence

import com.acme.identity.domain.SmsRateLimit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * Spring Data JPA repository for SMS rate limit records.
 *
 * Provides operations for tracking SMS sending history and enforcing
 * rate limits (max 3 SMS per hour per user).
 */
@Repository
interface SmsRateLimitRepository : JpaRepository<SmsRateLimit, UUID> {

    /**
     * Counts SMS sent to a user within a time window.
     *
     * @param userId The user ID to check.
     * @param since The start of the time window.
     * @return Count of SMS sent since the given timestamp.
     */
    @Query("SELECT COUNT(r) FROM SmsRateLimit r WHERE r.userId = :userId AND r.sentAt >= :since")
    fun countByUserIdSince(userId: UUID, since: Instant): Long

    /**
     * Gets the most recent SMS send time for a user.
     *
     * @param userId The user ID to check.
     * @return The most recent sent_at timestamp, or null if no SMS sent.
     */
    @Query("SELECT MAX(r.sentAt) FROM SmsRateLimit r WHERE r.userId = :userId")
    fun findMostRecentSentAt(userId: UUID): Instant?

    /**
     * Deletes SMS rate limit records older than the given timestamp.
     * Used for cleanup of stale records (older than 1 hour).
     *
     * @param before Delete records with sentAt before this timestamp.
     * @return Number of records deleted.
     */
    @Modifying
    @Query("DELETE FROM SmsRateLimit r WHERE r.sentAt < :before")
    fun deleteOlderThan(before: Instant): Int

    /**
     * Deletes all SMS rate limit records for a user.
     * Used for testing cleanup.
     *
     * @param userId The user ID.
     */
    @Modifying
    @Query("DELETE FROM SmsRateLimit r WHERE r.userId = :userId")
    fun deleteByUserId(userId: UUID)
}
