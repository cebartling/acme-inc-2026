package com.acme.notification.api.v1.dto

import com.acme.notification.domain.NotificationDelivery
import com.acme.notification.domain.NotificationStatus
import com.acme.notification.domain.NotificationType
import java.time.Instant
import java.util.UUID

/**
 * Response DTO for notification status queries.
 */
data class NotificationStatusResponse(
    val notificationId: UUID,
    val notificationType: NotificationType,
    val recipientId: UUID,
    val recipientEmail: String,
    val status: NotificationStatus,
    val attemptCount: Int,
    val providerMessageId: String?,
    val sentAt: Instant?,
    val deliveredAt: Instant?,
    val bouncedAt: Instant?,
    val bounceReason: String?,
    val createdAt: Instant
)

/** The delivery as the lookup endpoints and the read model's snapshots show it. */
fun NotificationDelivery.toStatusResponse() = NotificationStatusResponse(
    notificationId = id,
    notificationType = notificationType,
    recipientId = recipientId,
    recipientEmail = recipientEmail,
    status = status,
    attemptCount = attemptCount,
    providerMessageId = providerMessageId,
    sentAt = sentAt,
    deliveredAt = deliveredAt,
    bouncedAt = bouncedAt,
    bounceReason = bounceReason,
    createdAt = createdAt
)
