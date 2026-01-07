package com.acme.notification.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class NotificationDeliveryTest {

    @Test
    fun `should create notification delivery with default values`() {
        val delivery = NotificationDelivery(
            id = UUID.randomUUID(),
            notificationType = NotificationType.EMAIL_VERIFICATION,
            recipientId = UUID.randomUUID(),
            recipientEmail = "user@example.com",
            correlationId = "test-correlation-id"
        )

        assertEquals(NotificationStatus.PENDING, delivery.status)
        assertEquals(1, delivery.attemptCount)
        assertNull(delivery.providerMessageId)
        assertNull(delivery.sentAt)
        assertNull(delivery.deliveredAt)
        assertNull(delivery.bouncedAt)
        assertNull(delivery.bounceReason)
    }

    @Test
    fun `should mark delivery as sent`() {
        val delivery = createTestDelivery()
        val messageId = "sg-msg-123"

        delivery.markAsSent(messageId)

        assertEquals(NotificationStatus.SENT, delivery.status)
        assertEquals(messageId, delivery.providerMessageId)
        assertNotNull(delivery.sentAt)
    }

    @Test
    fun `should mark delivery as delivered`() {
        val delivery = createTestDelivery()
        delivery.markAsSent("sg-msg-123")

        delivery.markAsDelivered()

        assertEquals(NotificationStatus.DELIVERED, delivery.status)
        assertNotNull(delivery.deliveredAt)
    }

    @Test
    fun `should mark delivery as bounced with reason`() {
        val delivery = createTestDelivery()
        delivery.markAsSent("sg-msg-123")
        val bounceReason = "Mailbox not found"

        delivery.markAsBounced(bounceReason)

        assertEquals(NotificationStatus.BOUNCED, delivery.status)
        assertNotNull(delivery.bouncedAt)
        assertEquals(bounceReason, delivery.bounceReason)
    }

    @Test
    fun `should mark delivery as failed`() {
        val delivery = createTestDelivery()

        delivery.markAsFailed()

        assertEquals(NotificationStatus.FAILED, delivery.status)
    }

    @Test
    fun `should increment attempt count`() {
        val delivery = createTestDelivery()
        assertEquals(1, delivery.attemptCount)

        delivery.incrementAttemptCount()
        assertEquals(2, delivery.attemptCount)

        delivery.incrementAttemptCount()
        assertEquals(3, delivery.attemptCount)
    }



    @Test
    fun `equals should compare by id`() {
        val id = UUID.randomUUID()
        val delivery1 = NotificationDelivery(
            id = id,
            notificationType = NotificationType.EMAIL_VERIFICATION,
            recipientId = UUID.randomUUID(),
            recipientEmail = "user1@example.com"
        )
        val delivery2 = NotificationDelivery(
            id = id,
            notificationType = NotificationType.PASSWORD_RESET,
            recipientId = UUID.randomUUID(),
            recipientEmail = "user2@example.com"
        )
        val delivery3 = NotificationDelivery(
            id = UUID.randomUUID(),
            notificationType = NotificationType.EMAIL_VERIFICATION,
            recipientId = delivery1.recipientId,
            recipientEmail = "user1@example.com"
        )

        assertEquals(delivery1, delivery2)
        assertNotEquals(delivery1, delivery3)
    }

    @Test
    fun `hashCode should be based on id`() {
        val id = UUID.randomUUID()
        val delivery = NotificationDelivery(
            id = id,
            notificationType = NotificationType.EMAIL_VERIFICATION,
            recipientId = UUID.randomUUID(),
            recipientEmail = "user@example.com"
        )

        assertEquals(id.hashCode(), delivery.hashCode())
    }

    private fun createTestDelivery() = NotificationDelivery(
        id = UUID.randomUUID(),
        notificationType = NotificationType.EMAIL_VERIFICATION,
        recipientId = UUID.randomUUID(),
        recipientEmail = "user@example.com",
        correlationId = "test-correlation-id"
    )
}
