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
     * Deletes all expired used code records for cleanup.
     *
     * @param before Delete records that expired before this instant.
     * @return Number of deleted records.
     */
    @Modifying
    @Query("DELETE FROM UsedTotpCode c WHERE c.expiresAt < :before")
    fun deleteExpiredCodes(before: Instant): Int
}
