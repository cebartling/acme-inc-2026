package com.acme.notification.domain.events

import com.acme.notification.domain.NotificationStatus
import com.acme.notification.domain.NotificationType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class NotificationSentTest {

    @Test
    fun `should create NotificationSent event with correct values`() {
        val notificationId = UUID.randomUUID()
        val recipientId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val recipientEmail = "user@example.com"
        val providerMessageId = "sg-msg-123"

        val event = NotificationSent.create(
            notificationId = notificationId,
            type = NotificationType.EMAIL_VERIFICATION,
            recipientId = recipientId,
            recipientEmail = recipientEmail,
            providerMessageId = providerMessageId,
            status = NotificationStatus.SENT,
            correlationId = correlationId
        )

        assertEquals(NotificationSent.EVENT_TYPE, event.eventType)
        assertEquals(NotificationSent.EVENT_VERSION, event.eventVersion)
        assertEquals(NotificationSent.AGGREGATE_TYPE, event.aggregateType)
        assertEquals(notificationId, event.aggregateId)
        assertEquals(correlationId, event.correlationId)
        assertNotNull(event.eventId)
        assertNotNull(event.timestamp)

        // Verify payload
        assertEquals(notificationId, event.payload.notificationId)
        assertEquals(NotificationType.EMAIL_VERIFICATION, event.payload.type)
        assertEquals(recipientId, event.payload.recipientId)
        assertEquals(recipientEmail, event.payload.recipientEmail)
        assertEquals(providerMessageId, event.payload.providerMessageId)
        assertEquals(NotificationStatus.SENT, event.payload.status)
        assertNotNull(event.payload.sentAt)
    }

    @Test
    fun `should create event with null providerMessageId`() {
        val event = NotificationSent.create(
            notificationId = UUID.randomUUID(),
            type = NotificationType.EMAIL_VERIFICATION,
            recipientId = UUID.randomUUID(),
            recipientEmail = "user@example.com",
            providerMessageId = null,
            status = NotificationStatus.SENT,
            correlationId = UUID.randomUUID()
        )

        assertNull(event.payload.providerMessageId)
    }

    @Test
    fun `should generate unique event IDs for each event`() {
        val event1 = NotificationSent.create(
            notificationId = UUID.randomUUID(),
            type = NotificationType.EMAIL_VERIFICATION,
            recipientId = UUID.randomUUID(),
            recipientEmail = "user@example.com",
            providerMessageId = null,
            status = NotificationStatus.SENT,
            correlationId = UUID.randomUUID()
        )

        val event2 = NotificationSent.create(
            notificationId = UUID.randomUUID(),
            type = NotificationType.EMAIL_VERIFICATION,
            recipientId = UUID.randomUUID(),
            recipientEmail = "user@example.com",
            providerMessageId = null,
            status = NotificationStatus.SENT,
            correlationId = UUID.randomUUID()
        )

        assertNotEquals(event1.eventId, event2.eventId)
    }

    @Test
    fun `companion object should have correct constants`() {
        assertEquals("NotificationSent", NotificationSent.EVENT_TYPE)
        assertEquals("1.0", NotificationSent.EVENT_VERSION)
        assertEquals("Notification", NotificationSent.AGGREGATE_TYPE)
        assertEquals("notification.events", NotificationSent.TOPIC)
    }
}
