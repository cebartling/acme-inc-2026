package com.acme.notification.api.v1

import com.acme.notification.api.v1.dto.NotificationStatusResponse
import com.acme.notification.domain.NotificationStatus
import com.acme.notification.domain.NotificationType
import com.acme.notification.infrastructure.projection.NotificationReadModelQueries
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

/** The three lookup endpoints answer from the MongoDB read model (PIN-549). */
class NotificationControllerTest {

    private val queries: NotificationReadModelQueries = mockk()
    private val controller = NotificationController(queries)

    private fun response(id: UUID = UUID.randomUUID(), recipientId: UUID = UUID.randomUUID()) =
        NotificationStatusResponse(
            notificationId = id,
            notificationType = NotificationType.WELCOME,
            recipientId = recipientId,
            recipientEmail = "lookup@example.com",
            status = NotificationStatus.SENT,
            attemptCount = 1,
            providerMessageId = "msg-1",
            sentAt = Instant.parse("2026-11-17T12:00:00Z"),
            deliveredAt = null,
            bouncedAt = null,
            bounceReason = null,
            createdAt = Instant.parse("2026-11-17T11:59:59Z")
        )

    @Test
    fun `status by id returns the read model's snapshot`() {
        val snapshot = response()
        every { queries.findById(snapshot.notificationId) } returns snapshot

        val result = controller.getNotificationStatus(snapshot.notificationId)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(snapshot, result.body)
    }

    @Test
    fun `status by id is 404 when the read model has no such delivery`() {
        val id = UUID.randomUUID()
        every { queries.findById(id) } returns null

        assertEquals(HttpStatus.NOT_FOUND, controller.getNotificationStatus(id).statusCode)
    }

    @Test
    fun `by recipient returns that recipient's deliveries`() {
        val recipient = UUID.randomUUID()
        val snapshots = listOf(response(recipientId = recipient), response(recipientId = recipient))
        every { queries.findByRecipientId(recipient) } returns snapshots

        val result = controller.getNotificationsByRecipient(recipient)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(snapshots, result.body)
    }

    @Test
    fun `by email returns that address's deliveries, and an empty list when there are none`() {
        val snapshots = listOf(response())
        every { queries.findByRecipientEmail("lookup@example.com") } returns snapshots
        every { queries.findByRecipientEmail("nobody@example.com") } returns emptyList()

        assertEquals(snapshots, controller.getNotificationsByEmail("lookup@example.com").body)
        assertEquals(emptyList(), controller.getNotificationsByEmail("nobody@example.com").body)
    }
}
