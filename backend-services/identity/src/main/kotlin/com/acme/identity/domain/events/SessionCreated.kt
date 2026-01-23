package com.acme.identity.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Payload data for the [SessionCreated] domain event.
 *
 * Contains information about the newly created session for
 * session management and device tracking.
 *
 * @property sessionId The unique identifier of the session.
 * @property userId The unique identifier of the user.
 * @property deviceId The device identifier (fingerprint or explicit device ID).
 * @property ipAddress The IP address of the client making the request.
 * @property userAgent The User-Agent header from the request.
 * @property expiresAt When the session expires.
 */
data class SessionCreatedPayload(
    val sessionId: String,
    val userId: UUID,
    val deviceId: String,
    val ipAddress: String,
    val userAgent: String,
    val expiresAt: Instant
)

/**
 * Domain event published when a new session is created after successful authentication.
 *
 * This event is persisted to the event store and published to Kafka
 * to trigger downstream processes such as:
 * - Session tracking and management
 * - Device registration and tracking
 * - Analytics and reporting
 * - Security monitoring (e.g., concurrent session detection)
 *
 * @property payload The event payload containing session details.
 * @see DomainEvent
 * @see SessionCreatedPayload
 */
class SessionCreated(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: String,
    correlationId: UUID,
    val payload: SessionCreatedPayload
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
        const val EVENT_TYPE = "SessionCreated"

        /** The schema version for this event. */
        const val EVENT_VERSION = "1.0"

        /** The aggregate type this event belongs to. */
        const val AGGREGATE_TYPE = "Session"

        /** The Kafka topic where this event is published. */
        const val TOPIC = "identity.session.events"

        /**
         * Factory method to create a new [SessionCreated] event.
         *
         * Automatically generates the event ID and timestamp.
         *
         * @param sessionId The session's unique ID.
         * @param userId The user's unique ID.
         * @param deviceId The device identifier.
         * @param ipAddress The client's IP address.
         * @param userAgent The client's User-Agent header.
         * @param expiresAt When the session expires.
         * @param correlationId Optional correlation ID for distributed tracing.
         * @return A new [SessionCreated] event instance.
         */
        fun create(
            sessionId: String,
            userId: UUID,
            deviceId: String,
            ipAddress: String,
            userAgent: String,
            expiresAt: Instant,
            correlationId: UUID = UUID.randomUUID()
        ): SessionCreated {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return SessionCreated(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = sessionId,
                correlationId = correlationId,
                payload = SessionCreatedPayload(
                    sessionId = sessionId,
                    userId = userId,
                    deviceId = deviceId,
                    ipAddress = ipAddress,
                    userAgent = userAgent,
                    expiresAt = expiresAt
                )
            )
        }
    }
}
