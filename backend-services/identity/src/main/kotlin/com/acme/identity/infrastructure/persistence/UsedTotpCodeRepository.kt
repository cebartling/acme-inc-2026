package com.acme.identity.infrastructure.persistence

import com.acme.identity.domain.UsedTotpCode
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * Spring Data JPA repository for [UsedTotpCode] entities.
 *
 * Provides CRUD operations and custom query methods for tracking used TOTP codes.
 * Used to prevent replay attacks within the TOTP validity window.
 */
@Repository
interface UsedTotpCodeRepository : JpaRepository<UsedTotpCode, UUID> {

    /**
     * Checks if a TOTP code has already been used for a specific user and time step.
     *
     * @param userId The user's ID.
     * @param codeHash SHA-256 hash of the code.
     * @param timeStep The TOTP time step.
     * @return `true` if this code has been used, `false` otherwise.
     */
    fun existsByUserIdAndCodeHashAndTimeStep(userId: UUID, codeHash: String, timeStep: Long): Boolean

    /**
     * Checks if a TOTP code has already been used for a specific user within any of the given time steps.
     *
     * This method uses a single query with an IN clause to check multiple time steps,
     * avoiding the N+1 query pattern when checking code reuse across the TOTP tolerance window.
     *
     * @param userId The user's ID.
     * @param codeHash SHA-256 hash of the code.
     * @param timeSteps The TOTP time steps to check (typically current ±1 for tolerance).
     * @return `true` if this code has been used in any of the time steps, `false` otherwise.
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM UsedTotpCode c WHERE c.userId = :userId AND c.codeHash = :codeHash AND c.timeStep IN :timeSteps")
    fun existsByUserIdAndCodeHashAndTimeStepIn(userId: UUID, codeHash: String, timeSteps: Collection<Long>): Boolean

    /**
     * Deletes all expired used code records for cleanup.
     *
     * @param before Delete records that expired before this instant.
     * @return Number of deleted records.
     */
    @Modifying
    @Query("DELETE FROM UsedTotpCode c WHERE c.expiresAt < :before")
    fun deleteExpiredCodes(before: Instant): Int

    /**
     * Deletes all used TOTP codes for a user.
     * Used for test cleanup.
     *
     * @param userId The user's ID.
     */
    @Modifying
    @Query("DELETE FROM UsedTotpCode c WHERE c.userId = :userId")
    fun deleteByUserId(userId: UUID)
}
