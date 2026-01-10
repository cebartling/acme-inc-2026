package com.acme.customer.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Payload data for the [ProfileUpdated] domain event.
 *
 * Contains information about the profile update, including which fields
 * were changed and the new profile completeness score.
 *
 * @property customerId The unique identifier of the customer.
 * @property changedFields List of field names that were modified.
 * @property profileCompleteness The updated profile completeness percentage.
 */
data class ProfileUpdatedPayload(
    val customerId: UUID,
    val changedFields: List<String>,
    val profileCompleteness: Int
)

/**
 * Domain event published when a customer's profile is updated.
 *
 * This event is persisted to the event store and published to Kafka
 * to notify downstream services that a customer's profile has been modified.
 * The changedFields list indicates which specific fields were updated.
 *
 * @property payload The event payload containing update details.
 * @see DomainEvent
 * @see ProfileUpdatedPayload
 */
class ProfileUpdated(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    causationId: UUID,
    val payload: ProfileUpdatedPayload
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
        const val EVENT_TYPE = "ProfileUpdated"

        /** The schema version for this event. */
        const val EVENT_VERSION = "1.0"

        /** The aggregate type this event belongs to. */
        const val AGGREGATE_TYPE = "Customer"

        /** The Kafka topic where this event is published. */
        const val TOPIC = "customer.events"

        /**
         * Factory method to create a new [ProfileUpdated] event.
         *
         * Automatically generates the event ID and timestamp.
         *
         * @param customerId The unique identifier for the customer.
         * @param changedFields List of field names that were modified.
         * @param profileCompleteness The updated profile completeness percentage.
         * @param correlationId Correlation ID for distributed tracing.
         * @param causationId The event ID or request ID that caused this update.
         * @return A new [ProfileUpdated] event instance.
         */
        fun create(
            customerId: UUID,
            changedFields: List<String>,
            profileCompleteness: Int,
            correlationId: UUID,
            causationId: UUID
        ): ProfileUpdated {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return ProfileUpdated(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = customerId,
                correlationId = correlationId,
                causationId = causationId,
                payload = ProfileUpdatedPayload(
                    customerId = customerId,
                    changedFields = changedFields,
                    profileCompleteness = profileCompleteness
                )
            )
        }
    }
}
