package com.acme.notification.domain.events

import com.acme.notification.domain.NotificationStatus
import com.acme.notification.domain.NotificationType
import java.time.Instant
import java.util.UUID

/**
 * Payload data for the [NotificationSent] domain event.
 *
 * @property notificationId The unique identifier of the notification.
 * @property type The type of notification sent.
 * @property recipientId The ID of the user who received the notification.
 * @property recipientEmail The email address of the recipient.
 * @property providerMessageId The message ID from the email provider.
 * @property status The delivery status.
 * @property sentAt When the notification was sent.
 */
data class NotificationSentPayload(
    val notificationId: UUID,
    val type: NotificationType,
    val recipientId: UUID,
    val recipientEmail: String,
    val providerMessageId: String?,
    val status: NotificationStatus,
    val sentAt: Instant
)

/**
 * Domain event published when a notification is sent.
 *
 * This event is published to Kafka to inform other services
 * that a notification has been sent to a user.
 *
 * @property payload The event payload containing notification details.
 */
class NotificationSent(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: NotificationSentPayload
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
        const val EVENT_TYPE = "NotificationSent"
        const val EVENT_VERSION = "1.0"
        const val AGGREGATE_TYPE = "Notification"
        const val TOPIC = "notification.events"

        /**
         * Factory method to create a new [NotificationSent] event.
         */
        fun create(
            notificationId: UUID,
            type: NotificationType,
            recipientId: UUID,
            recipientEmail: String,
            providerMessageId: String?,
            status: NotificationStatus,
            correlationId: UUID
        ): NotificationSent {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return NotificationSent(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = notificationId,
                correlationId = correlationId,
                payload = NotificationSentPayload(
                    notificationId = notificationId,
                    type = type,
                    recipientId = recipientId,
                    recipientEmail = recipientEmail,
                    providerMessageId = providerMessageId,
                    status = status,
                    sentAt = timestamp
                )
            )
        }
    }
}
