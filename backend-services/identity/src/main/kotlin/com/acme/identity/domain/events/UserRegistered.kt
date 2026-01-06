package com.acme.identity.domain.events

import com.acme.identity.domain.RegistrationSource
import java.time.Instant
import java.util.UUID

/**
 * Payload data for the [UserRegistered] domain event.
 *
 * Contains all the information about the newly registered user that
 * downstream consumers may need to process.
 *
 * @property userId The unique identifier assigned to the new user.
 * @property email The user's email address.
 * @property firstName The user's first name.
 * @property lastName The user's last name.
 * @property tosAcceptedAt When the user accepted the Terms of Service.
 * @property marketingOptIn Whether the user opted in to marketing communications.
 * @property registrationSource The channel through which the user registered.
 * @property verificationToken The email verification token for the user.
 */
data class UserRegisteredPayload(
    val userId: UUID,
    val email: String,
    val firstName: String,
    val lastName: String,
    val tosAcceptedAt: Instant,
    val marketingOptIn: Boolean,
    val registrationSource: RegistrationSource,
    val verificationToken: String
)

/**
 * Domain event published when a new user successfully registers.
 *
 * This event is persisted to the event store and published to Kafka
 * to trigger downstream processes such as:
 * - Sending verification emails
 * - Creating customer profiles in other services
 * - Analytics and reporting
 *
 * @property payload The event payload containing user registration details.
 * @see DomainEvent
 * @see UserRegisteredPayload
 */
class UserRegistered(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: UserRegisteredPayload
) : DomainEvent(
    eventId = eventId,
    eventType = EVENT_TYPE,
    eventVersion = EVENT_VERSION,
    timestamp = timestamp,
    aggregateId = aggregateId,
    aggregateType = AGGREGATE_TYPE,
    correlationId = correlationId
) {
    companion object {
        /** The event type identifier. */
        const val EVENT_TYPE = "UserRegistered"

        /** The schema version for this event. */
        const val EVENT_VERSION = "1.0"

        /** The aggregate type this event belongs to. */
        const val AGGREGATE_TYPE = "User"

        /** The Kafka topic where this event is published. */
        const val TOPIC = "identity.user.events"

        /**
         * Factory method to create a new [UserRegistered] event.
         *
         * Automatically generates the event ID and timestamp.
         *
         * @param userId The unique identifier for the new user.
         * @param email The user's email address.
         * @param firstName The user's first name.
         * @param lastName The user's last name.
         * @param tosAcceptedAt When the user accepted the Terms of Service.
         * @param marketingOptIn Whether the user opted in to marketing.
         * @param registrationSource The registration channel.
         * @param verificationToken The email verification token.
         * @param correlationId Optional correlation ID for distributed tracing.
         * @return A new [UserRegistered] event instance.
         */
        fun create(
            userId: UUID,
            email: String,
            firstName: String,
            lastName: String,
            tosAcceptedAt: Instant,
            marketingOptIn: Boolean,
            registrationSource: RegistrationSource,
            verificationToken: String,
            correlationId: UUID = UUID.randomUUID()
        ): UserRegistered {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return UserRegistered(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = userId,
                correlationId = correlationId,
                payload = UserRegisteredPayload(
                    userId = userId,
                    email = email,
                    firstName = firstName,
                    lastName = lastName,
                    tosAcceptedAt = tosAcceptedAt,
                    marketingOptIn = marketingOptIn,
                    registrationSource = registrationSource,
                    verificationToken = verificationToken
                )
            )
        }
    }
}
