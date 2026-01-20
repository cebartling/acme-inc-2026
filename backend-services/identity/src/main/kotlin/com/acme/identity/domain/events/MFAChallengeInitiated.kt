package com.acme.identity.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Payload data for the [MFAChallengeInitiated] domain event.
 *
 * Contains information about the initiated MFA challenge.
 *
 * @property userId The ID of the user being challenged.
 * @property mfaToken The token identifying this MFA challenge.
 * @property method The MFA method being used (TOTP, SMS, EMAIL).
 * @property expiresAt When the challenge expires.
 */
data class MFAChallengeInitiatedPayload(
    val userId: UUID,
    val mfaToken: String,
    val method: String,
    val expiresAt: Instant
)

/**
 * Domain event published when an MFA challenge is initiated.
 *
 * This event is persisted to the event store and published to Kafka
 * after a user successfully validates their credentials and needs
 * to complete MFA verification.
 *
 * @property payload The event payload containing challenge details.
 * @see DomainEvent
 * @see MFAChallengeInitiatedPayload
 */
class MFAChallengeInitiated(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: MFAChallengeInitiatedPayload
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
        const val EVENT_TYPE = "MFAChallengeInitiated"

        /** The schema version for this event. */
        const val EVENT_VERSION = "1.0"

        /** The aggregate type this event belongs to. */
        const val AGGREGATE_TYPE = "User"

        /** The Kafka topic where this event is published. */
        const val TOPIC = "identity.mfa.events"

        /**
         * Factory method to create a new [MFAChallengeInitiated] event.
         *
         * Automatically generates the event ID and timestamp.
         *
         * @param userId The user ID.
         * @param mfaToken The challenge token.
         * @param method The MFA method being used.
         * @param expiresAt When the challenge expires.
         * @param correlationId Optional correlation ID for distributed tracing.
         * @return A new [MFAChallengeInitiated] event instance.
         */
        fun create(
            userId: UUID,
            mfaToken: String,
            method: String,
            expiresAt: Instant,
            correlationId: UUID = UUID.randomUUID()
        ): MFAChallengeInitiated {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return MFAChallengeInitiated(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = userId,
                correlationId = correlationId,
                payload = MFAChallengeInitiatedPayload(
                    userId = userId,
                    mfaToken = mfaToken,
                    method = method,
                    expiresAt = expiresAt
                )
            )
        }
    }
}
