package com.acme.customer.domain.events

import com.acme.customer.domain.CustomerStatus
import com.acme.customer.domain.CustomerType
import java.time.Instant
import java.util.UUID

/**
 * Payload data for the [CustomerRegistered] domain event.
 *
 * Contains all the information about the newly created customer profile
 * that downstream consumers may need to process.
 *
 * @property customerId The unique identifier assigned to the customer.
 * @property userId The corresponding user ID from the Identity Service.
 * @property customerNumber The human-readable customer number (ACME-YYYYMM-NNNNNN).
 * @property email The customer's email address.
 * @property firstName The customer's first name.
 * @property lastName The customer's last name.
 * @property status The initial customer status.
 * @property type The customer account type.
 * @property registeredAt When the customer profile was created.
 */
data class CustomerRegisteredPayload(
    val customerId: UUID,
    val userId: UUID,
    val customerNumber: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val status: CustomerStatus,
    val type: CustomerType,
    val registeredAt: Instant
)

/**
 * Domain event published when a new customer profile is created.
 *
 * This event is persisted to the event store and published to Kafka
 * to notify downstream services that a customer profile has been created.
 * The event is triggered by consumption of a UserRegistered event from
 * the Identity Service.
 *
 * @property payload The event payload containing customer profile details.
 * @see DomainEvent
 * @see CustomerRegisteredPayload
 */
class CustomerRegistered(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    causationId: UUID,
    val payload: CustomerRegisteredPayload
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
        const val EVENT_TYPE = "CustomerRegistered"

        /** The schema version for this event. */
        const val EVENT_VERSION = "1.0"

        /** The aggregate type this event belongs to. */
        const val AGGREGATE_TYPE = "Customer"

        /** The Kafka topic where this event is published. */
        const val TOPIC = "customer.events"

        /**
         * Factory method to create a new [CustomerRegistered] event.
         *
         * Automatically generates the event ID and timestamp.
         *
         * @param customerId The unique identifier for the customer.
         * @param userId The corresponding user ID from Identity Service.
         * @param customerNumber The human-readable customer number.
         * @param email The customer's email address.
         * @param firstName The customer's first name.
         * @param lastName The customer's last name.
         * @param status The customer's status.
         * @param type The customer account type.
         * @param registeredAt When the customer registered.
         * @param correlationId Correlation ID for distributed tracing.
         * @param causationId The event ID of the triggering UserRegistered event.
         * @return A new [CustomerRegistered] event instance.
         */
        fun create(
            customerId: UUID,
            userId: UUID,
            customerNumber: String,
            email: String,
            firstName: String,
            lastName: String,
            status: CustomerStatus,
            type: CustomerType,
            registeredAt: Instant,
            correlationId: UUID,
            causationId: UUID
        ): CustomerRegistered {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return CustomerRegistered(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = customerId,
                correlationId = correlationId,
                causationId = causationId,
                payload = CustomerRegisteredPayload(
                    customerId = customerId,
                    userId = userId,
                    customerNumber = customerNumber,
                    email = email,
                    firstName = firstName,
                    lastName = lastName,
                    status = status,
                    type = type,
                    registeredAt = registeredAt
                )
            )
        }
    }
}
