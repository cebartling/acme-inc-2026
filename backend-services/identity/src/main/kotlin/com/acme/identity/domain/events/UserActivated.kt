package com.acme.identity.domain.events

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
 * Payload data for the [UserActivated] domain event.
 *
 * Contains the information about the activated user account that
 * downstream consumers may need to process.
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
 * Domain event published when a user account is activated.
 *
 * This event is persisted to the event store and published to Kafka
 * to trigger downstream processes such as:
 * - Enabling user access to protected resources
 * - Sending welcome/activation confirmation emails
 * - Updating customer profiles in other services
 * - Analytics and reporting
 *
 * @property payload The event payload containing activation details.
 * @see DomainEvent
 * @see UserActivatedPayload
 */
class UserActivated(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: UserActivatedPayload
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
        const val EVENT_TYPE = "UserActivated"

        /** The schema version for this event. */
        const val EVENT_VERSION = "1.0"

        /** The aggregate type this event belongs to. */
        const val AGGREGATE_TYPE = "User"

        /** The Kafka topic where this event is published. */
        const val TOPIC = "identity.user.events"

        /**
         * Factory method to create a new [UserActivated] event.
         *
         * Automatically generates the event ID and timestamp.
         *
         * @param userId The unique identifier for the user.
         * @param activatedAt When the account was activated.
         * @param activationMethod The method by which the account was activated.
         * @param correlationId Optional correlation ID for distributed tracing.
         * @return A new [UserActivated] event instance.
         */
        fun create(
            userId: UUID,
            activatedAt: Instant = Instant.now(),
            activationMethod: ActivationMethod = ActivationMethod.EMAIL_VERIFICATION,
            correlationId: UUID = UUID.randomUUID()
        ): UserActivated {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return UserActivated(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = userId,
                correlationId = correlationId,
                payload = UserActivatedPayload(
                    userId = userId,
                    activatedAt = activatedAt,
                    activationMethod = activationMethod
                )
            )
        }
    }
}
