package com.acme.identity.infrastructure.persistence

import com.acme.identity.domain.MfaChallenge
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * Spring Data JPA repository for [MfaChallenge] entities.
 *
 * Provides CRUD operations and custom query methods for MFA challenge persistence.
 * Challenges are short-lived (5 minutes) and should be cleaned up periodically.
 */
@Repository
interface MfaChallengeRepository : JpaRepository<MfaChallenge, UUID> {

    /**
     * Finds an MFA challenge by its token.
     *
     * @param token The unique challenge token.
     * @return The [MfaChallenge] if found, or `null` if no challenge has this token.
     */
    fun findByToken(token: String): MfaChallenge?

    /**
     * Finds all active challenges for a user.
     *
     * @param userId The user's ID.
     * @return List of MFA challenges for this user.
     */
    fun findByUserId(userId: UUID): List<MfaChallenge>

    /**
     * Deletes all challenges for a user.
     * Called after successful MFA verification or when starting a new signin.
     *
     * @param userId The user's ID.
     */
    @Modifying
    @Query("DELETE FROM MfaChallenge c WHERE c.userId = :userId")
    fun deleteByUserId(userId: UUID)

    /**
     * Deletes all expired challenges for cleanup.
     *
     * @param before Delete challenges that expired before this instant.
     * @return Number of deleted challenges.
     */
    @Modifying
    @Query("DELETE FROM MfaChallenge c WHERE c.expiresAt < :before")
    fun deleteExpiredChallenges(before: Instant): Int

    /**
     * Expires a challenge immediately by setting its expiry time to the past.
     * This is used for testing purposes only.
     *
     * @param token The challenge token.
     * @param expiredAt The expiry time to set (should be in the past).
     * @return Number of updated challenges (0 or 1).
     */
    @Modifying
    @Query("UPDATE MfaChallenge c SET c.expiresAt = :expiredAt WHERE c.token = :token")
    fun expireByToken(token: String, expiredAt: Instant): Int

    /**
     * Resets the lastSentAt timestamp to bypass the SMS resend cooldown.
     * This is used for testing purposes only.
     *
     * @param token The challenge token.
     * @param lastSentAt The timestamp to set (should be in the past).
     * @return Number of updated challenges (0 or 1).
     */
    @Modifying
    @Query("UPDATE MfaChallenge c SET c.lastSentAt = :lastSentAt WHERE c.token = :token")
    fun resetLastSentAt(token: String, lastSentAt: Instant): Int
}
