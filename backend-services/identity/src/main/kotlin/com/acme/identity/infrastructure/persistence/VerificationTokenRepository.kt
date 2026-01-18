package com.acme.identity.infrastructure.persistence

import com.acme.identity.domain.VerificationToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Spring Data JPA repository for [VerificationToken] entities.
 *
 * Provides CRUD operations and custom query methods for email
 * verification token persistence.
 */
@Repository
interface VerificationTokenRepository : JpaRepository<VerificationToken, UUID> {

    /**
     * Finds a verification token by its token string.
     *
     * Used when a user clicks the verification link in their email.
     *
     * @param token The token string from the verification URL.
     * @return The [VerificationToken] if found, or `null` if invalid.
     */
    fun findByToken(token: String): VerificationToken?

    /**
     * Finds all verification tokens for a specific user.
     *
     * A user may have multiple tokens if they requested verification
     * emails multiple times. Older tokens should be invalidated when
     * any token is successfully used.
     *
     * @param userId The ID of the user.
     * @return List of all tokens for this user.
     */
    fun findByUserId(userId: UUID): List<VerificationToken>

    /**
     * Deletes all verification tokens for a specific user.
     *
     * Used for test cleanup when deleting a user.
     *
     * @param userId The ID of the user.
     */
    fun deleteByUserId(userId: UUID)
}
