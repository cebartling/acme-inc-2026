package com.acme.notification.infrastructure.messaging.dto

import java.time.Instant
import java.util.UUID

/**
 * Payload data from the CustomerActivated event.
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
 * DTO for deserializing CustomerActivated events from Kafka.
 *
 * This represents the event structure published by the Customer Service
 * when a customer profile is activated. The Notification Service consumes
 * these events to send welcome emails.
 *
 * @property eventId Unique identifier for this event.
 * @property eventType The type of event (should be "CustomerActivated").
 * @property eventVersion Schema version of the event.
 * @property timestamp When the event occurred.
 * @property aggregateId The aggregate ID (customer ID).
 * @property aggregateType The aggregate type (should be "Customer").
 * @property correlationId ID for distributed tracing.
 * @property causationId The event ID that caused this event.
 * @property payload The event payload with activation details.
 */
data class CustomerActivatedEvent(
    val eventId: UUID,
    val eventType: String,
    val eventVersion: String,
    val timestamp: Instant,
    val aggregateId: UUID,
    val aggregateType: String,
    val correlationId: UUID,
    val causationId: UUID,
    val payload: CustomerActivatedPayload
) {
    companion object {
        const val EVENT_TYPE = "CustomerActivated"
        const val TOPIC = "customer.events"
    }
}
