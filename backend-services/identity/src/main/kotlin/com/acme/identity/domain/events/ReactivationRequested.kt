package com.acme.identity.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Payload for [ReactivationRequested], emitted when a DEACTIVATED customer
 * asks for a reactivation email. The notification service consumes this
 * event to send the reactivation link.
 */
data class ReactivationRequestedPayload(
    val userId: UUID,
    val email: String,
    val firstName: String,
    val lastName: String,
    val reactivationToken: String,
    val expiresAt: Instant
)

class ReactivationRequested(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: ReactivationRequestedPayload
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
        const val EVENT_TYPE = "ReactivationRequested"
        const val EVENT_VERSION = "1.0"
        const val AGGREGATE_TYPE = "User"
        const val TOPIC = "identity.user.events"

        fun create(
            userId: UUID,
            email: String,
            firstName: String,
            lastName: String,
            reactivationToken: String,
            expiresAt: Instant,
            correlationId: UUID = UUID.randomUUID()
        ): ReactivationRequested {
            return ReactivationRequested(
                eventId = UUID.randomUUID(),
                timestamp = Instant.now(),
                aggregateId = userId,
                correlationId = correlationId,
                payload = ReactivationRequestedPayload(
                    userId = userId,
                    email = email,
                    firstName = firstName,
                    lastName = lastName,
                    reactivationToken = reactivationToken,
                    expiresAt = expiresAt
                )
            )
        }
    }
}
