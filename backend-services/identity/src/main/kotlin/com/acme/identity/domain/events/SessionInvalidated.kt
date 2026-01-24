package com.acme.identity.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Payload data for the [SessionInvalidated] domain event.
 *
 * Contains information about why a session was invalidated
 * for security monitoring and session management.
 *
 * @property sessionId The unique identifier of the invalidated session.
 * @property userId The unique identifier of the user.
 * @property reason The reason for session invalidation (e.g., CONCURRENT_SESSION_LIMIT, LOGOUT, EXPIRED).
 * @property invalidatedAt When the session was invalidated.
 */
data class SessionInvalidatedPayload(
    val sessionId: String,
    val userId: UUID,
    val reason: String,
    val invalidatedAt: Instant
)

/**
 * Domain event published when a session is invalidated.
 *
 * Sessions can be invalidated for several reasons:
 * - CONCURRENT_SESSION_LIMIT: Exceeded maximum concurrent sessions (oldest evicted)
 * - LOGOUT: User explicitly logged out
 * - EXPIRED: Session TTL expired
 * - SECURITY: Security concern (suspicious activity, password change, etc.)
 *
 * This event is persisted to the event store and published to Kafka
 * to trigger downstream processes such as:
 * - Session cleanup and revocation
 * - Security monitoring and alerts
 * - Analytics and reporting
 * - Client notification (if active)
 *
 * @property payload The event payload containing invalidation details.
 * @see DomainEvent
 * @see SessionInvalidatedPayload
 */
class SessionInvalidated(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: String,
    correlationId: UUID,
    val payload: SessionInvalidatedPayload
) : DomainEvent(
    eventId = eventId,
    eventType = EVENT_TYPE,
    eventVersion = EVENT_VERSION,
    timestamp = timestamp,
    aggregateId = UUID.fromString(aggregateId.removePrefix("sess_")),
    aggregateType = AGGREGATE_TYPE,
    correlationId = correlationId
) {
    companion object {
        /** The event type identifier. */
        const val EVENT_TYPE = "SessionInvalidated"

        /** The schema version for this event. */
        const val EVENT_VERSION = "1.0"

        /** The aggregate type this event belongs to. */
        const val AGGREGATE_TYPE = "Session"

        /** The Kafka topic where this event is published. */
        const val TOPIC = "identity.session.events"

        /** Reason: Session exceeded concurrent session limit and was evicted. */
        const val REASON_CONCURRENT_LIMIT = "CONCURRENT_SESSION_LIMIT"

        /** Reason: User explicitly logged out. */
        const val REASON_LOGOUT = "LOGOUT"

        /** Reason: Session TTL expired. */
        const val REASON_EXPIRED = "EXPIRED"

        /** Reason: Session invalidated for security reasons. */
        const val REASON_SECURITY = "SECURITY"

        /**
         * Factory method to create a new [SessionInvalidated] event.
         *
         * Automatically generates the event ID and timestamp.
         *
         * @param sessionId The session's unique ID.
         * @param userId The user's unique ID.
         * @param reason The reason for invalidation.
         * @param correlationId Optional correlation ID for distributed tracing.
         * @return A new [SessionInvalidated] event instance.
         */
        fun create(
            sessionId: String,
            userId: UUID,
            reason: String,
            correlationId: UUID = UUID.randomUUID()
        ): SessionInvalidated {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return SessionInvalidated(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = sessionId,
                correlationId = correlationId,
                payload = SessionInvalidatedPayload(
                    sessionId = sessionId,
                    userId = userId,
                    reason = reason,
                    invalidatedAt = timestamp
                )
            )
        }
    }
}
