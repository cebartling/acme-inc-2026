package com.acme.customer.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Payload data for the [CustomerActivated] domain event.
 *
 * Contains all the information about the activated customer that
 * downstream consumers may need to process.
 *
 * @property customerId The unique identifier of the customer.
 * @property activatedAt When the customer was activated.
 * @property emailVerified Whether the customer's email is now verified.
 */
data class CustomerActivatedPayload(
    val customerId: UUID,
    val activatedAt: Instant,
    val emailVerified: Boolean
)

/**
 * Domain event published when a customer profile is activated.
 *
 * This event is persisted to the event store and published to Kafka
 * to notify downstream services that a customer profile has been activated.
 * The event is triggered by consumption of a UserActivated event from
 * the Identity Service.
 *
 * @property payload The event payload containing activation details.
 * @see DomainEvent
 * @see CustomerActivatedPayload
 */
class CustomerActivated(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    causationId: UUID,
    val payload: CustomerActivatedPayload
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
        const val EVENT_TYPE = "CustomerActivated"

        /** The schema version for this event. */
        const val EVENT_VERSION = "1.0"

        /** The aggregate type this event belongs to. */
        const val AGGREGATE_TYPE = "Customer"

        /** The Kafka topic where this event is published. */
        const val TOPIC = "customer.events"

        /**
         * Factory method to create a new [CustomerActivated] event.
         *
         * Automatically generates the event ID and timestamp.
         *
         * @param customerId The unique identifier for the customer.
         * @param activatedAt When the customer was activated.
         * @param emailVerified Whether the email is verified.
         * @param correlationId Correlation ID for distributed tracing.
         * @param causationId The event ID of the triggering UserActivated event.
         * @return A new [CustomerActivated] event instance.
         */
        fun create(
            customerId: UUID,
            activatedAt: Instant,
            emailVerified: Boolean,
            correlationId: UUID,
            causationId: UUID
        ): CustomerActivated {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return CustomerActivated(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = customerId,
                correlationId = correlationId,
                causationId = causationId,
                payload = CustomerActivatedPayload(
                    customerId = customerId,
                    activatedAt = activatedAt,
                    emailVerified = emailVerified
                )
            )
        }
    }
}
