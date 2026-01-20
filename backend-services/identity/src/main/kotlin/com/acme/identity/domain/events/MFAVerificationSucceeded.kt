package com.acme.identity.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Payload data for the [MFAVerificationSucceeded] domain event.
 *
 * Contains information about the successful MFA verification.
 *
 * @property userId The ID of the user who completed MFA.
 * @property method The MFA method that was used (TOTP, SMS, EMAIL).
 * @property deviceRemembered Whether the user opted to remember this device.
 */
data class MFAVerificationSucceededPayload(
    val userId: UUID,
    val method: String,
    val deviceRemembered: Boolean
)

/**
 * Domain event published when MFA verification succeeds.
 *
 * This event is persisted to the event store and published to Kafka
 * after a user successfully completes their MFA challenge.
 *
 * @property payload The event payload containing verification details.
 * @see DomainEvent
 * @see MFAVerificationSucceededPayload
 */
class MFAVerificationSucceeded(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: MFAVerificationSucceededPayload
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
        const val EVENT_TYPE = "MFAVerificationSucceeded"

        /** The schema version for this event. */
        const val EVENT_VERSION = "1.0"

        /** The aggregate type this event belongs to. */
        const val AGGREGATE_TYPE = "User"

        /** The Kafka topic where this event is published. */
        const val TOPIC = "identity.mfa.events"

        /**
         * Factory method to create a new [MFAVerificationSucceeded] event.
         *
         * Automatically generates the event ID and timestamp.
         *
         * @param userId The user ID.
         * @param method The MFA method that was used.
         * @param deviceRemembered Whether the device was remembered.
         * @param correlationId Optional correlation ID for distributed tracing.
         * @return A new [MFAVerificationSucceeded] event instance.
         */
        fun create(
            userId: UUID,
            method: String,
            deviceRemembered: Boolean,
            correlationId: UUID = UUID.randomUUID()
        ): MFAVerificationSucceeded {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return MFAVerificationSucceeded(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = userId,
                correlationId = correlationId,
                payload = MFAVerificationSucceededPayload(
                    userId = userId,
                    method = method,
                    deviceRemembered = deviceRemembered
                )
            )
        }
    }
}
