package com.acme.notification.infrastructure.messaging.dto

import java.time.Instant
import java.util.UUID

data class PasswordResetRequestedPayload(
    val userId: UUID,
    val email: String,
    val firstName: String,
    val lastName: String,
    val resetToken: String,
    val expiresAt: Instant,
    val ipAddress: String? = null
)

data class PasswordResetRequestedEvent(
    val eventId: UUID,
    val eventType: String,
    val eventVersion: String,
    val timestamp: Instant,
    val aggregateId: UUID,
    val aggregateType: String,
    val correlationId: UUID,
    val payload: PasswordResetRequestedPayload
) {
    companion object {
        const val EVENT_TYPE = "PasswordResetRequested"
        const val TOPIC = "identity.user.events"
    }
}
