package com.acme.customer.infrastructure.messaging.dto

import java.time.Instant
import java.util.UUID

/**
 * Enumeration of possible methods by which a user account can be activated.
 */
enum class ActivationMethod {
    /** Account activated via email verification link. */
    EMAIL_VERIFICATION,

    /** Account activated by an administrator. */
    ADMIN_ACTIVATION,

    /** Account activated via phone verification. */
    PHONE_VERIFICATION
}

/**
 * Payload data from the UserActivated event.
 *
 * @property userId The unique identifier of the user.
 * @property activatedAt When the account was activated.
 * @property activationMethod The method by which the account was activated.
 */
data class UserActivatedPayload(
    val userId: UUID,
    val activatedAt: Instant,
    val activationMethod: ActivationMethod
)

/**
 * DTO for deserializing UserActivated events from Kafka.
 *
 * This represents the event structure published by the Identity Service
 * when a user account is activated (typically after email verification).
 * The Customer Service consumes these events to activate customer profiles.
 *
 * @property eventId Unique identifier for this event.
 * @property eventType The type of event (should be "UserActivated").
 * @property eventVersion Schema version of the event.
 * @property timestamp When the event occurred.
 * @property aggregateId The aggregate ID (user ID).
 * @property aggregateType The aggregate type (should be "User").
 * @property correlationId ID for distributed tracing.
 * @property payload The event payload with activation details.
 */
data class UserActivatedEvent(
    val eventId: UUID,
    val eventType: String,
    val eventVersion: String,
    val timestamp: Instant,
    val aggregateId: UUID,
    val aggregateType: String,
    val correlationId: UUID,
    val payload: UserActivatedPayload
) {
    companion object {
        const val EVENT_TYPE = "UserActivated"
        const val TOPIC = "identity.user.events"
    }
}
