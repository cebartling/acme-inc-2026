package com.acme.identity.infrastructure.persistence

import com.acme.identity.domain.Session
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Spring Data Redis repository for [Session] entities.
 *
 * Provides CRUD operations and custom query methods for session persistence.
 * Sessions are stored in Redis with automatic TTL-based expiration.
 *
 * The repository supports:
 * - Finding all sessions for a specific user
 * - Automatic session expiration via Redis TTL
 * - Indexed queries on userId for efficient lookups
 */
@Repository
interface SessionRepository : CrudRepository<Session, String> {

    /**
     * Finds all active sessions for a given user.
     *
     * This is used for:
     * - Enforcing concurrent session limits (max 5 per user)
     * - Identifying the oldest session for eviction
     * - Session management and monitoring
     *
     * @param userId The user's unique identifier.
     * @return A list of all active sessions for the user.
     */
    fun findByUserId(userId: UUID): List<Session>

    /**
     * Counts the number of active sessions for a given user.
     *
     * This is a more efficient alternative to calling findByUserId
     * when you only need the count for limit enforcement.
     *
     * @param userId The user's unique identifier.
     * @return The number of active sessions for the user.
     */
    fun countByUserId(userId: UUID): Long
}
