package com.acme.customer.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Payload data for the [AddressRemoved] domain event.
 *
 * Contains information about the removed address including its type
 * and whether it was a default address.
 *
 * @property customerId The unique identifier of the customer.
 * @property addressId The unique identifier of the removed address.
 * @property type The type of the removed address (SHIPPING or BILLING).
 * @property wasDefault Whether this was the default address for its type.
 */
data class AddressRemovedPayload(
    val customerId: UUID,
    val addressId: UUID,
    val type: String,
    val wasDefault: Boolean
)

/**
 * Domain event published when an address is removed from a customer profile.
 *
 * This event is persisted to the event store and published to Kafka
 * to notify downstream services (Order, Cart) that an address has been
 * deleted. Services should handle cases where a referenced address
 * no longer exists.
 *
 * @property payload The event payload containing removal details.
 * @see DomainEvent
 * @see AddressRemovedPayload
 */
class AddressRemoved(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    causationId: UUID,
    val payload: AddressRemovedPayload
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
        const val EVENT_TYPE = "AddressRemoved"

        /** The schema version for this event. */
        const val EVENT_VERSION = "1.0"

        /** The aggregate type this event belongs to. */
        const val AGGREGATE_TYPE = "Customer"

        /** The Kafka topic where this event is published. */
        const val TOPIC = "customer.events"

        /**
         * Factory method to create a new [AddressRemoved] event.
         *
         * Automatically generates the event ID and timestamp.
         *
         * @param customerId The unique identifier for the customer.
         * @param addressId The unique identifier for the removed address.
         * @param type The address type (SHIPPING or BILLING).
         * @param wasDefault Whether this was the default address.
         * @param correlationId Correlation ID for distributed tracing.
         * @param causationId The event ID or request ID that caused this removal.
         * @return A new [AddressRemoved] event instance.
         */
        fun create(
            customerId: UUID,
            addressId: UUID,
            type: String,
            wasDefault: Boolean,
            correlationId: UUID,
            causationId: UUID
        ): AddressRemoved {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return AddressRemoved(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = customerId,
                correlationId = correlationId,
                causationId = causationId,
                payload = AddressRemovedPayload(
                    customerId = customerId,
                    addressId = addressId,
                    type = type,
                    wasDefault = wasDefault
                )
            )
        }
    }
}
