package com.acme.customer.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Payload data for the [AddressAdded] domain event.
 *
 * Contains information about the newly added address including type,
 * validation status, and whether it is the default address.
 *
 * @property customerId The unique identifier of the customer.
 * @property addressId The unique identifier of the new address.
 * @property type The type of address (SHIPPING or BILLING).
 * @property label Optional label for the address.
 * @property isDefault Whether this is the default address for its type.
 * @property isValidated Whether the address has been validated.
 */
data class AddressAddedPayload(
    val customerId: UUID,
    val addressId: UUID,
    val type: String,
    val label: String?,
    val isDefault: Boolean,
    val isValidated: Boolean
)

/**
 * Domain event published when a new address is added to a customer profile.
 *
 * This event is persisted to the event store and published to Kafka
 * to notify downstream services (Order, Cart) that a customer has added
 * a new address.
 *
 * @property payload The event payload containing address details.
 * @see DomainEvent
 * @see AddressAddedPayload
 */
class AddressAdded(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    causationId: UUID,
    val payload: AddressAddedPayload
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
        const val EVENT_TYPE = "AddressAdded"

        /** The schema version for this event. */
        const val EVENT_VERSION = "1.0"

        /** The aggregate type this event belongs to. */
        const val AGGREGATE_TYPE = "Customer"

        /** The Kafka topic where this event is published. */
        const val TOPIC = "customer.events"

        /**
         * Factory method to create a new [AddressAdded] event.
         *
         * Automatically generates the event ID and timestamp.
         *
         * @param customerId The unique identifier for the customer.
         * @param addressId The unique identifier for the new address.
         * @param type The address type (SHIPPING or BILLING).
         * @param label Optional label for the address.
         * @param isDefault Whether this is the default address.
         * @param isValidated Whether the address has been validated.
         * @param correlationId Correlation ID for distributed tracing.
         * @param causationId The event ID or request ID that caused this event.
         * @return A new [AddressAdded] event instance.
         */
        fun create(
            customerId: UUID,
            addressId: UUID,
            type: String,
            label: String?,
            isDefault: Boolean,
            isValidated: Boolean,
            correlationId: UUID,
            causationId: UUID
        ): AddressAdded {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return AddressAdded(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = customerId,
                correlationId = correlationId,
                causationId = causationId,
                payload = AddressAddedPayload(
                    customerId = customerId,
                    addressId = addressId,
                    type = type,
                    label = label,
                    isDefault = isDefault,
                    isValidated = isValidated
                )
            )
        }
    }
}
