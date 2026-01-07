package com.acme.identity.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Payload data for the [EmailVerified] domain event.
 *
 * Contains the information about the verified email address that
 * downstream consumers may need to process.
 *
 * @property userId The unique identifier of the user.
 * @property email The user's verified email address.
 * @property verifiedAt When the email was verified.
 */
data class EmailVerifiedPayload(
    val userId: UUID,
    val email: String,
    val verifiedAt: Instant
)

/**
 * Domain event published when a user successfully verifies their email.
 *
 * This event is persisted to the event store and published to Kafka
 * to trigger downstream processes such as:
 * - Updating customer profiles in other services
 * - Sending welcome emails
 * - Analytics and reporting
 *
 * @property payload The event payload containing verification details.
 * @see DomainEvent
 * @see EmailVerifiedPayload
 */
class EmailVerified(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: EmailVerifiedPayload
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
        const val EVENT_TYPE = "EmailVerified"

        /** The schema version for this event. */
        const val EVENT_VERSION = "1.0"

        /** The aggregate type this event belongs to. */
        const val AGGREGATE_TYPE = "User"

        /** The Kafka topic where this event is published. */
        const val TOPIC = "identity.user.events"

        /**
         * Factory method to create a new [EmailVerified] event.
         *
         * Automatically generates the event ID and timestamp.
         *
         * @param userId The unique identifier for the user.
         * @param email The user's verified email address.
         * @param verifiedAt When the email was verified.
         * @param correlationId Optional correlation ID for distributed tracing.
         * @return A new [EmailVerified] event instance.
         */
        fun create(
            userId: UUID,
            email: String,
            verifiedAt: Instant = Instant.now(),
            correlationId: UUID = UUID.randomUUID()
        ): EmailVerified {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return EmailVerified(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = userId,
                correlationId = correlationId,
                payload = EmailVerifiedPayload(
                    userId = userId,
                    email = email,
                    verifiedAt = verifiedAt
                )
            )
        }
    }
}
