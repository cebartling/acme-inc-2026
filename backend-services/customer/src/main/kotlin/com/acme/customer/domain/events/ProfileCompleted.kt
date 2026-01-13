package com.acme.customer.domain.events

import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Payload data for the [ProfileCompleted] domain event.
 *
 * Contains information about the profile completion, including when it was
 * completed and how long it took from registration.
 *
 * @property customerId The unique identifier of the customer.
 * @property completedAt Timestamp when the profile reached 100% completion.
 * @property timeToComplete Duration from registration to profile completion (ISO 8601 format).
 */
data class ProfileCompletedPayload(
    val customerId: UUID,
    val completedAt: Instant,
    val timeToComplete: String
)

/**
 * Domain event published when a customer's profile reaches 100% completion.
 *
 * This event is triggered when a customer completes all sections of their profile:
 * - Basic Info (name, verified email)
 * - Contact Info (phone number)
 * - Personal Details (date of birth or gender)
 * - Address (validated address)
 * - Preferences (communication preferences)
 * - Consent (required consents)
 *
 * The event is persisted to the event store and published to Kafka for
 * downstream services (e.g., loyalty rewards, welcome campaigns).
 *
 * @property payload The event payload containing completion details.
 * @see DomainEvent
 * @see ProfileCompletedPayload
 */
class ProfileCompleted(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    causationId: UUID? = null,
    val payload: ProfileCompletedPayload
) : DomainEvent(
    eventId = eventId,
    eventType = EVENT_TYPE,
    eventVersion = EVENT_VERSION,
    timestamp = timestamp,
    aggregateId = aggregateId,
    aggregateType = AGGREGATE_TYPE,
    correlationId = correlationId,
    causationId = causationId
) {
    companion object {
        /** The event type identifier. */
        const val EVENT_TYPE = "ProfileCompleted"

        /** The schema version for this event. */
        const val EVENT_VERSION = "1.0"

        /** The aggregate type this event belongs to. */
        const val AGGREGATE_TYPE = "Customer"

        /** The Kafka topic where this event is published. */
        const val TOPIC = "customer.events"

        /**
         * Factory method to create a new [ProfileCompleted] event.
         *
         * Automatically generates the event ID and timestamp.
         *
         * @param customerId The unique identifier for the customer.
         * @param registeredAt The customer's registration timestamp.
         * @param correlationId Correlation ID for distributed tracing.
         * @param causationId The event ID or request ID that triggered this completion.
         * @return A new [ProfileCompleted] event instance.
         */
        fun create(
            customerId: UUID,
            registeredAt: Instant,
            correlationId: UUID,
            causationId: UUID? = null
        ): ProfileCompleted {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()
            val timeToComplete = Duration.between(registeredAt, timestamp)

            return ProfileCompleted(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = customerId,
                correlationId = correlationId,
                causationId = causationId,
                payload = ProfileCompletedPayload(
                    customerId = customerId,
                    completedAt = timestamp,
                    timeToComplete = formatDuration(timeToComplete)
                )
            )
        }

        /**
         * Formats a duration as ISO 8601 duration string.
         *
         * @param duration The duration to format.
         * @return ISO 8601 formatted duration string (e.g., "PT2H30M").
         */
        private fun formatDuration(duration: Duration): String {
            return duration.toString()
        }
    }
}
