package com.acme.notification.infrastructure.messaging.dto

import java.time.Instant
import java.util.UUID

/**
 * Payload data from the UserRegistered event.
 *
 * @property userId The unique identifier assigned to the user.
 * @property email The user's email address.
 * @property firstName The user's first name.
 * @property lastName The user's last name.
 * @property tosAcceptedAt When the user accepted the Terms of Service.
 * @property marketingOptIn Whether the user opted in to marketing communications.
 * @property registrationSource The channel through which the user registered.
 * @property verificationToken The email verification token.
 */
data class UserRegisteredPayload(
    val userId: UUID,
    val email: String,
    val firstName: String,
    val lastName: String,
    val tosAcceptedAt: Instant,
    val marketingOptIn: Boolean,
    val registrationSource: String,
    val verificationToken: String? = null
)

/**
 * DTO for deserializing UserRegistered events from Kafka.
 *
 * This represents the event structure published by the Identity Service
 * when a new user registers. The Notification Service consumes these events
 * to send verification emails.
 *
 * @property eventId Unique identifier for this event.
 * @property eventType The type of event (should be "UserRegistered").
 * @property eventVersion Schema version of the event.
 * @property timestamp When the event occurred.
 * @property aggregateId The aggregate ID (user ID).
 * @property aggregateType The aggregate type (should be "User").
 * @property correlationId ID for distributed tracing.
 * @property payload The event payload with user details.
 */
data class UserRegisteredEvent(
    val eventId: UUID,
    val eventType: String,
    val eventVersion: String,
    val timestamp: Instant,
    val aggregateId: UUID,
    val aggregateType: String,
    val correlationId: UUID,
    val payload: UserRegisteredPayload
) {
    companion object {
        const val EVENT_TYPE = "UserRegistered"
        const val TOPIC = "identity.user.events"
    }
}
