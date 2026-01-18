package com.acme.identity.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Payload data for the [AuthenticationSucceeded] domain event.
 *
 * Contains information about the successful authentication for
 * auditing and session management.
 *
 * @property userId The unique identifier of the authenticated user.
 * @property email The user's email address.
 * @property ipAddress The IP address of the client making the request.
 * @property userAgent The User-Agent header from the request.
 * @property mfaRequired Whether MFA verification is still required.
 * @property deviceFingerprint Optional device fingerprint from the request.
 */
data class AuthenticationSucceededPayload(
    val userId: UUID,
    val email: String,
    val ipAddress: String,
    val userAgent: String,
    val mfaRequired: Boolean,
    val deviceFingerprint: String? = null
)

/**
 * Domain event published when an authentication attempt succeeds.
 *
 * This event is persisted to the event store and published to Kafka
 * to trigger downstream processes such as:
 * - Session management
 * - Audit logging
 * - Analytics and reporting
 * - Fraud detection (e.g., new device detection)
 *
 * Note: This event is published after credential validation but before
 * MFA verification (if required). The mfaRequired field indicates
 * whether the user still needs to complete MFA.
 *
 * @property payload The event payload containing success details.
 * @see DomainEvent
 * @see AuthenticationSucceededPayload
 */
class AuthenticationSucceeded(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: AuthenticationSucceededPayload
) : DomainEvent(
    eventId = eventId,
    eventType = EVENT_TYPE,
    eventVersion = EVENT_VERSION,
    timestamp = timestamp,
    aggregateId = aggregateId,
    aggregateType = AGGREGATE_TYPE,
    correlationId = correlationId
) {
    companion object {
        /** The event type identifier. */
        const val EVENT_TYPE = "AuthenticationSucceeded"

        /** The schema version for this event. */
        const val EVENT_VERSION = "1.0"

        /** The aggregate type this event belongs to. */
        const val AGGREGATE_TYPE = "User"

        /** The Kafka topic where this event is published. */
        const val TOPIC = "identity.authentication.events"

        /**
         * Factory method to create a new [AuthenticationSucceeded] event.
         *
         * Automatically generates the event ID and timestamp.
         *
         * @param userId The authenticated user's ID.
         * @param email The user's email address.
         * @param ipAddress The client's IP address.
         * @param userAgent The client's User-Agent header.
         * @param mfaRequired Whether MFA verification is still required.
         * @param deviceFingerprint Optional device fingerprint.
         * @param correlationId Optional correlation ID for distributed tracing.
         * @return A new [AuthenticationSucceeded] event instance.
         */
        fun create(
            userId: UUID,
            email: String,
            ipAddress: String,
            userAgent: String,
            mfaRequired: Boolean,
            deviceFingerprint: String? = null,
            correlationId: UUID = UUID.randomUUID()
        ): AuthenticationSucceeded {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return AuthenticationSucceeded(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = userId,
                correlationId = correlationId,
                payload = AuthenticationSucceededPayload(
                    userId = userId,
                    email = email,
                    ipAddress = ipAddress,
                    userAgent = userAgent,
                    mfaRequired = mfaRequired,
                    deviceFingerprint = deviceFingerprint
                )
            )
        }
    }
}
