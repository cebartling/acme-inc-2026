package com.acme.identity.infrastructure.persistence

import com.acme.identity.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Spring Data JPA repository for [User] entities.
 *
 * Provides CRUD operations and custom query methods for user persistence.
 * Uses PostgreSQL as the underlying database.
 */
@Repository
interface UserRepository : JpaRepository<User, UUID> {

    /**
     * Checks if a user with the given email address already exists.
     *
     * Used for duplicate detection during registration. The check
     * should be performed with the email in lowercase for consistency.
     *
     * @param email The email address to check.
     * @return `true` if a user with this email exists, `false` otherwise.
     */
    fun existsByEmail(email: String): Boolean

    /**
     * Finds a user by their email address.
     *
     * @param email The email address to search for.
     * @return The [User] if found, or `null` if no user has this email.
     */
    fun findByEmail(email: String): User?
}
