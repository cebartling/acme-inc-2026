package com.acme.customer.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Payload data for the [AddressUpdated] domain event.
 *
 * Contains information about the updated address including which fields
 * were changed, the new validation status, and default address status.
 *
 * @property customerId The unique identifier of the customer.
 * @property addressId The unique identifier of the updated address.
 * @property changedFields List of field names that were modified.
 * @property isDefault Whether this is now the default address for its type.
 * @property isValidated Whether the address has been validated.
 */
data class AddressUpdatedPayload(
    val customerId: UUID,
    val addressId: UUID,
    val changedFields: List<String>,
    val isDefault: Boolean,
    val isValidated: Boolean
)

/**
 * Domain event published when an existing address is updated.
 *
 * This event is persisted to the event store and published to Kafka
 * to notify downstream services that an address has been modified.
 * The changedFields list indicates which specific fields were updated.
 *
 * @property payload The event payload containing update details.
 * @see DomainEvent
 * @see AddressUpdatedPayload
 */
class AddressUpdated(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    causationId: UUID,
    val payload: AddressUpdatedPayload
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
        const val EVENT_TYPE = "AddressUpdated"

        /** The schema version for this event. */
        const val EVENT_VERSION = "1.0"

        /** The aggregate type this event belongs to. */
        const val AGGREGATE_TYPE = "Customer"

        /** The Kafka topic where this event is published. */
        const val TOPIC = "customer.events"

        /**
         * Factory method to create a new [AddressUpdated] event.
         *
         * Automatically generates the event ID and timestamp.
         *
         * @param customerId The unique identifier for the customer.
         * @param addressId The unique identifier for the updated address.
         * @param changedFields List of field names that were modified.
         * @param isDefault Whether this is the default address.
         * @param isValidated Whether the address has been validated.
         * @param correlationId Correlation ID for distributed tracing.
         * @param causationId The event ID or request ID that caused this update.
         * @return A new [AddressUpdated] event instance.
         */
        fun create(
            customerId: UUID,
            addressId: UUID,
            changedFields: List<String>,
            isDefault: Boolean,
            isValidated: Boolean,
            correlationId: UUID,
            causationId: UUID
        ): AddressUpdated {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return AddressUpdated(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = customerId,
                correlationId = correlationId,
                causationId = causationId,
                payload = AddressUpdatedPayload(
                    customerId = customerId,
                    addressId = addressId,
                    changedFields = changedFields,
                    isDefault = isDefault,
                    isValidated = isValidated
                )
            )
        }
    }
}
