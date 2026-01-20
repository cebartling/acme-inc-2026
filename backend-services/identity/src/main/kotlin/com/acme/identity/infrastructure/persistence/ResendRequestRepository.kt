package com.acme.identity.infrastructure.persistence

import com.acme.identity.domain.VerificationResendRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * Spring Data JPA repository for [VerificationResendRequest] entities.
 *
 * Provides CRUD operations and custom query methods for tracking
 * verification email resend requests for rate limiting purposes.
 */
@Repository
interface ResendRequestRepository : JpaRepository<VerificationResendRequest, UUID> {

    /**
     * Counts the number of resend requests for an email since a given time.
     *
     * Used for rate limiting to enforce maximum requests per time window.
     *
     * @param email The email address to check.
     * @param since The start of the time window.
     * @return The count of requests in the time window.
     */
    @Query("SELECT COUNT(r) FROM VerificationResendRequest r WHERE r.email = :email AND r.requestedAt >= :since")
    fun countByEmailSince(email: String, since: Instant): Long

    /**
     * Finds the oldest resend request for an email since a given time.
     *
     * Used to calculate when the rate limit will reset.
     *
     * @param email The email address to check.
     * @param since The start of the time window.
     * @return The oldest request in the time window, or null if none.
     */
    @Query("SELECT r FROM VerificationResendRequest r WHERE r.email = :email AND r.requestedAt >= :since ORDER BY r.requestedAt ASC LIMIT 1")
    fun findOldestByEmailSince(email: String, since: Instant): VerificationResendRequest?

    /**
     * Deletes old resend requests for cleanup purposes.
     *
     * Requests older than the retention period can be safely deleted
     * as they are no longer relevant for rate limiting.
     *
     * @param before The timestamp before which to delete requests.
     */
    @Modifying
    @Query("DELETE FROM VerificationResendRequest r WHERE r.requestedAt < :before")
    fun deleteOlderThan(before: Instant)

    /**
     * Deletes all resend requests for an email.
     * Used for test cleanup.
     *
     * @param email The email address.
     */
    @Modifying
    @Query("DELETE FROM VerificationResendRequest r WHERE r.email = :email")
    fun deleteByEmail(email: String)
}
