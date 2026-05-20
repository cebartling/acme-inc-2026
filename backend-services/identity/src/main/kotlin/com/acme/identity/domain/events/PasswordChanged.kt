package com.acme.identity.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Reason a password was changed. PASSWORD_RESET indicates a customer
 * completed the forgot-password flow; PASSWORD_CHANGE indicates an
 * authenticated change-password action.
 */
enum class PasswordChangeReason {
    PASSWORD_RESET,
    PASSWORD_CHANGE
}

/**
 * Payload for [PasswordChanged], emitted after a customer's password
 * hash has been updated. Records how many sessions and device trusts
 * were invalidated as a side effect so downstream consumers (security
 * monitoring, audit) have a complete picture.
 */
data class PasswordChangedPayload(
    val userId: UUID,
    val reason: PasswordChangeReason,
    val sessionsInvalidated: Int,
    val deviceTrustsRevoked: Int,
    val ipAddress: String?
)

class PasswordChanged(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: PasswordChangedPayload
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
        const val EVENT_TYPE = "PasswordChanged"
        const val EVENT_VERSION = "1.0"
        const val AGGREGATE_TYPE = "User"
        const val TOPIC = "identity.user.events"

        fun create(
            userId: UUID,
            reason: PasswordChangeReason,
            sessionsInvalidated: Int,
            deviceTrustsRevoked: Int,
            ipAddress: String?,
            correlationId: UUID = UUID.randomUUID()
        ): PasswordChanged {
            return PasswordChanged(
                eventId = UUID.randomUUID(),
                timestamp = Instant.now(),
                aggregateId = userId,
                correlationId = correlationId,
                payload = PasswordChangedPayload(
                    userId = userId,
                    reason = reason,
                    sessionsInvalidated = sessionsInvalidated,
                    deviceTrustsRevoked = deviceTrustsRevoked,
                    ipAddress = ipAddress
                )
            )
        }
    }
}
