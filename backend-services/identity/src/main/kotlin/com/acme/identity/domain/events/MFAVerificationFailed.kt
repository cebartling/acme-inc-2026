package com.acme.identity.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Reason codes for MFA verification failures.
 */
enum class MFAVerificationFailureReason {
    /** The MFA token is invalid or not found. */
    INVALID_TOKEN,

    /** The MFA challenge has expired. */
    CHALLENGE_EXPIRED,

    /** The verification code was invalid. */
    INVALID_CODE,

    /** The code has already been used. */
    CODE_ALREADY_USED,

    /** Maximum attempts exceeded. */
    MAX_ATTEMPTS_EXCEEDED
}

/**
 * Payload data for the [MFAVerificationFailed] domain event.
 *
 * Contains information about the failed MFA verification attempt.
 *
 * @property userId The ID of the user whose verification failed.
 * @property method The MFA method that was used (TOTP, SMS, EMAIL).
 * @property reason The reason for the failure.
 * @property attemptCount The number of attempts made on this challenge.
 */
data class MFAVerificationFailedPayload(
    val userId: UUID,
    val method: String,
    val reason: MFAVerificationFailureReason,
    val attemptCount: Int
)

/**
 * Domain event published when MFA verification fails.
 *
 * This event is persisted to the event store and published to Kafka
 * when a user fails to complete their MFA challenge.
 *
 * @property payload The event payload containing failure details.
 * @see DomainEvent
 * @see MFAVerificationFailedPayload
 */
class MFAVerificationFailed(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: MFAVerificationFailedPayload
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
        const val EVENT_TYPE = "MFAVerificationFailed"

        /** The schema version for this event. */
        const val EVENT_VERSION = "1.0"

        /** The aggregate type this event belongs to. */
        const val AGGREGATE_TYPE = "User"

        /** The Kafka topic where this event is published. */
        const val TOPIC = "identity.mfa.events"

        /**
         * Factory method to create a new [MFAVerificationFailed] event.
         *
         * Automatically generates the event ID and timestamp.
         *
         * @param userId The user ID.
         * @param method The MFA method that was used.
         * @param reason The reason for the failure.
         * @param attemptCount The number of attempts made.
         * @param correlationId Optional correlation ID for distributed tracing.
         * @return A new [MFAVerificationFailed] event instance.
         */
        fun create(
            userId: UUID,
            method: String,
            reason: MFAVerificationFailureReason,
            attemptCount: Int,
            correlationId: UUID = UUID.randomUUID()
        ): MFAVerificationFailed {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return MFAVerificationFailed(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = userId,
                correlationId = correlationId,
                payload = MFAVerificationFailedPayload(
                    userId = userId,
                    method = method,
                    reason = reason,
                    attemptCount = attemptCount
                )
            )
        }
    }
}
