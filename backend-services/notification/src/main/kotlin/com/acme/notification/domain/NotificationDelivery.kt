package com.acme.notification.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * JPA entity representing a notification delivery record.
 *
 * Tracks all delivery attempts for notifications, including status,
 * provider message IDs, and retry information.
 *
 * @property id Unique identifier for the delivery record.
 * @property notificationType Type of notification being sent.
 * @property recipientId The ID of the user receiving the notification.
 * @property recipientEmail Email address of the recipient.
 * @property providerMessageId Message ID returned by the email provider.
 * @property status Current delivery status.
 * @property attemptCount Number of delivery attempts made.
 * @property sentAt Timestamp when the notification was sent.
 * @property deliveredAt Timestamp when delivery was confirmed.
 * @property bouncedAt Timestamp when a bounce was detected.
 * @property bounceReason Reason for the bounce, if applicable.
 * @property correlationId ID for distributed tracing.
 * @property createdAt Timestamp when the record was created.
 * @property updatedAt Timestamp when the record was last updated.
 */
@Entity
@Table(name = "notification_deliveries")
class NotificationDelivery(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    val notificationType: NotificationType,

    @Column(name = "recipient_id", nullable = false)
    val recipientId: UUID,

    @Column(name = "recipient_email", nullable = false, length = 255)
    val recipientEmail: String,

    @Column(name = "provider_message_id", length = 255)
    var providerMessageId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    var status: NotificationStatus = NotificationStatus.PENDING,

    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 1,

    @Column(name = "sent_at")
    var sentAt: Instant? = null,

    @Column(name = "delivered_at")
    var deliveredAt: Instant? = null,

    @Column(name = "bounced_at")
    var bouncedAt: Instant? = null,

    @Column(name = "bounce_reason", length = 500)
    var bounceReason: String? = null,

    @Column(name = "correlation_id", length = 100)
    val correlationId: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    /**
     * Marks the delivery as sent.
     *
     * @param messageId The message ID from the email provider.
     */
    fun markAsSent(messageId: String?) {
        status = NotificationStatus.SENT
        providerMessageId = messageId
        sentAt = Instant.now()
        updatedAt = Instant.now()
    }

    /**
     * Marks the delivery as delivered.
     */
    fun markAsDelivered() {
        status = NotificationStatus.DELIVERED
        deliveredAt = Instant.now()
        updatedAt = Instant.now()
    }

    /**
     * Marks the delivery as bounced.
     *
     * @param reason The bounce reason.
     */
    fun markAsBounced(reason: String) {
        status = NotificationStatus.BOUNCED
        bouncedAt = Instant.now()
        bounceReason = reason
        updatedAt = Instant.now()
    }

    /**
     * Marks the delivery as failed.
     */
    fun markAsFailed() {
        status = NotificationStatus.FAILED
        updatedAt = Instant.now()
    }

    /**
     * Increments the attempt count for retry tracking.
     */
    fun incrementAttemptCount() {
        attemptCount++
        updatedAt = Instant.now()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NotificationDelivery) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
