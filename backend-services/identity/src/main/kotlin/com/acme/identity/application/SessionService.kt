package com.acme.identity.application

import com.acme.identity.config.SessionConfig
import com.acme.identity.domain.Session
import com.acme.identity.domain.events.SessionCreated
import com.acme.identity.domain.events.SessionInvalidated
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.SessionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Service for managing user sessions.
 *
 * Responsibilities:
 * - Create new sessions with automatic TTL
 * - Enforce concurrent session limits (max 5 per user)
 * - Evict oldest sessions when limit is exceeded
 * - Publish SessionCreated and SessionInvalidated events
 *
 * Sessions are stored in Redis with automatic expiration after 7 days.
 * When a user exceeds the maximum concurrent sessions (5), the oldest
 * session is automatically evicted to make room for the new one.
 *
 * @property sessionRepository Repository for session storage in Redis.
 * @property eventPublisher Publisher for domain events.
 * @property config Session configuration (max sessions, TTL).
 */
@Service
class SessionService(
    private val sessionRepository: SessionRepository,
    private val eventPublisher: UserEventPublisher,
    private val config: SessionConfig
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Creates a new session for the user.
     *
     * Steps:
     * 1. Enforce concurrent session limit (evict oldest if needed)
     * 2. Create and save the new session
     * 3. Publish SessionCreated event
     *
     * @param userId The user's unique ID.
     * @param deviceId The device identifier (fingerprint).
     * @param ipAddress The client's IP address.
     * @param userAgent The client's User-Agent header.
     * @param tokenFamily The token family for refresh token rotation.
     * @return The created [Session].
     */
    fun createSession(
        userId: UUID,
        deviceId: String,
        ipAddress: String,
        userAgent: String,
        tokenFamily: String
    ): Session {
        logger.debug("Creating session for user $userId")

        // Enforce concurrent session limit
        evictOldestSessionIfNeeded(userId)

        // Create new session
        val session = Session.create(
            userId = userId,
            deviceId = deviceId,
            ipAddress = ipAddress,
            userAgent = userAgent,
            tokenFamily = tokenFamily,
            ttlSeconds = config.ttlSeconds
        )

        // Save to Redis
        sessionRepository.save(session)
        logger.info("Created session ${session.id} for user $userId")

        // Publish event
        val event = SessionCreated.create(
            sessionId = session.id,
            userId = userId,
            deviceId = deviceId,
            ipAddress = ipAddress,
            userAgent = userAgent,
            expiresAt = session.expiresAt
        )
        eventPublisher.publish(event)

        return session
    }

    /**
     * Enforces the concurrent session limit for a user.
     *
     * If the user already has the maximum number of sessions (5),
     * the oldest session is evicted to make room for a new one.
     *
     * A SessionInvalidated event is published for the evicted session.
     *
     * Note: This implementation has a known race condition where concurrent
     * logins may temporarily exceed the session limit. The check-then-act
     * pattern is not atomic. For production, this should use Redis Lua scripts
     * or distributed locking to ensure atomicity.
     *
     * @param userId The user's unique ID.
     */
    private fun evictOldestSessionIfNeeded(userId: UUID) {
        val sessions = sessionRepository.findByUserId(userId)

        if (sessions.size >= config.maxPerUser) {
            val oldest = sessions.minByOrNull { it.createdAt }
                ?: run {
                    logger.error("Session list is not empty but minByOrNull returned null for user $userId")
                    return
                }

            logger.info("Evicting oldest session ${oldest.id} for user $userId (limit: ${config.maxPerUser})")

            // Delete the session
            sessionRepository.delete(oldest)

            // Publish SessionInvalidated event
            val event = SessionInvalidated.create(
                sessionId = oldest.id,
                userId = userId,
                reason = SessionInvalidated.REASON_CONCURRENT_LIMIT
            )
            eventPublisher.publish(event)
        }
    }

    /**
     * Invalidates a specific session.
     *
     * Used for explicit logout or security-related invalidation.
     * Publishes a SessionInvalidated event with the given reason.
     *
     * @param sessionId The session ID to invalidate.
     * @param userId The user's unique ID.
     * @param reason The reason for invalidation (LOGOUT, SECURITY, etc.).
     */
    fun invalidateSession(sessionId: String, userId: UUID, reason: String) {
        logger.info("Invalidating session $sessionId for user $userId (reason: $reason)")

        // Delete the session
        sessionRepository.deleteById(sessionId)

        // Publish event
        val event = SessionInvalidated.create(
            sessionId = sessionId,
            userId = userId,
            reason = reason
        )
        eventPublisher.publish(event)
    }

    /**
     * Finds a session by its ID.
     *
     * @param sessionId The session ID.
     * @return The [Session] if found, null otherwise.
     */
    fun findSession(sessionId: String): Session? {
        return sessionRepository.findById(sessionId).orElse(null)
    }

    /**
     * Finds all active sessions for a user.
     *
     * @param userId The user's unique ID.
     * @return A list of active sessions.
     */
    fun findUserSessions(userId: UUID): List<Session> {
        return sessionRepository.findByUserId(userId)
    }
}
