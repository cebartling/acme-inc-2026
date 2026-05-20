package com.acme.identity.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Payload for [PasswordResetRequested], emitted when a customer asks
 * for a password reset email. The notification service consumes this
 * event to deliver the reset link.
 */
data class PasswordResetRequestedPayload(
    val userId: UUID,
    val email: String,
    val firstName: String,
    val lastName: String,
    val resetToken: String,
    val expiresAt: Instant,
    val ipAddress: String?
)

class PasswordResetRequested(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: PasswordResetRequestedPayload
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
        const val EVENT_TYPE = "PasswordResetRequested"
        const val EVENT_VERSION = "1.0"
        const val AGGREGATE_TYPE = "User"
        const val TOPIC = "identity.user.events"

        fun create(
            userId: UUID,
            email: String,
            firstName: String,
            lastName: String,
            resetToken: String,
            expiresAt: Instant,
            ipAddress: String?,
            correlationId: UUID = UUID.randomUUID()
        ): PasswordResetRequested {
            return PasswordResetRequested(
                eventId = UUID.randomUUID(),
                timestamp = Instant.now(),
                aggregateId = userId,
                correlationId = correlationId,
                payload = PasswordResetRequestedPayload(
                    userId = userId,
                    email = email,
                    firstName = firstName,
                    lastName = lastName,
                    resetToken = resetToken,
                    expiresAt = expiresAt,
                    ipAddress = ipAddress
                )
            )
        }
    }
}
