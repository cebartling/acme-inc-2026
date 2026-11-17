package com.acme.notification.infrastructure.messaging

import com.acme.notification.domain.NotificationStatus
import com.acme.notification.domain.NotificationType
import com.acme.notification.domain.events.NotificationSent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class NotificationEventPublisherTest {

    private val outboxWriter: OutboxWriter = mockk(relaxed = true)
    private val publisher = NotificationEventPublisher(outboxWriter)

    private fun event() = NotificationSent.create(
        notificationId = UUID.randomUUID(),
        type = NotificationType.EMAIL_VERIFICATION,
        recipientId = UUID.randomUUID(),
        recipientEmail = "publisher@example.com",
        providerMessageId = "msg-1",
        status = NotificationStatus.SENT,
        correlationId = UUID.randomUUID()
    )

    @Test
    fun `publish writes the event to the outbox for the notification events topic`() {
        val event = event()

        publisher.publish(event)

        verify(exactly = 1) { outboxWriter.write(event, "notification.events") }
    }

    @Test
    fun `a failed outbox write is not swallowed`() {
        every { outboxWriter.write(any(), any()) } throws IllegalStateException("outbox down")

        assertThrows<IllegalStateException> { publisher.publish(event()) }
    }
}
