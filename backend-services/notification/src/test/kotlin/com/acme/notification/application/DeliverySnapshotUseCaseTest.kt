package com.acme.notification.application

import com.acme.notification.domain.NotificationDelivery
import com.acme.notification.domain.NotificationStatus
import com.acme.notification.domain.events.NotificationSent
import com.acme.notification.infrastructure.email.EmailSendResult
import com.acme.notification.infrastructure.email.SendGridEmailSender
import com.acme.notification.infrastructure.messaging.NotificationEventPublisher
import com.acme.notification.infrastructure.messaging.OutboxWriteException
import com.acme.notification.infrastructure.persistence.NotificationDeliveryRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the three send use cases write to the outbox (PIN-549): after the delivery's final state is
 * saved, a snapshot of it for the read model, in the same transaction; after a successful send, also
 * the NotificationSent event, whose id the snapshot follows. A failed outbox write fails the use case.
 */
class DeliverySnapshotUseCaseTest {

    private val emailSender: SendGridEmailSender = mockk()
    private val deliveryRepository: NotificationDeliveryRepository = mockk()
    private val eventPublisher: NotificationEventPublisher = mockk(relaxed = true)
    private val meterRegistry = SimpleMeterRegistry()

    private val userId = UUID.randomUUID()
    private val correlationId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        every { deliveryRepository.save(any()) } answers { firstArg() }
        every { deliveryRepository.existsByRecipientIdAndNotificationType(any(), any()) } returns false
    }

    private fun verification() = SendVerificationEmailUseCase(emailSender, deliveryRepository, eventPublisher, meterRegistry)
        .let { { it.execute(userId, "u@example.com", "Una", "token", correlationId) } }

    private fun passwordReset() = SendPasswordResetEmailUseCase(emailSender, deliveryRepository, eventPublisher, meterRegistry)
        .let { { it.execute(userId, "u@example.com", "Una", "token", correlationId) } }

    private fun welcome() = SendWelcomeEmailUseCase(emailSender, deliveryRepository, eventPublisher, meterRegistry)
        .let { { it.execute(userId, "u@example.com", "Una", "Una U", "ACME-1", false, 100, correlationId) } }

    private fun sendSucceeds() {
        every { emailSender.sendVerificationEmail(any(), any(), any(), any()) } returns EmailSendResult.Success("msg-1", 202)
        every { emailSender.sendPasswordResetEmail(any(), any(), any(), any()) } returns EmailSendResult.Success("msg-1", 202)
        every { emailSender.sendWelcomeEmail(any(), any(), any(), any(), any(), any(), any()) } returns
            EmailSendResult.Success("msg-1", 202)
    }

    private fun sendFails() {
        every { emailSender.sendVerificationEmail(any(), any(), any(), any()) } returns EmailSendResult.Failure("boom", null)
        every { emailSender.sendPasswordResetEmail(any(), any(), any(), any()) } returns EmailSendResult.Failure("boom", null)
        every { emailSender.sendWelcomeEmail(any(), any(), any(), any(), any(), any(), any()) } returns
            EmailSendResult.Failure("boom", null)
    }

    private val useCases = mapOf(
        "verification" to this::verification,
        "password reset" to this::passwordReset,
        "welcome" to this::welcome
    )

    @Test
    fun `a sent notification writes the event and a SENT snapshot that follows it`() {
        for ((name, build) in useCases) {
            sendSucceeds()
            val event = slot<NotificationSent>()
            val delivery = slot<NotificationDelivery>()
            val sourceEventId = slot<UUID>()
            io.mockk.clearMocks(eventPublisher, answers = false, recordedCalls = true)

            build()()

            verify(exactly = 1) { eventPublisher.publish(capture(event)) }
            verify(exactly = 1) { eventPublisher.publishSnapshot(capture(delivery), capture(sourceEventId)) }
            assertEquals(NotificationStatus.SENT, delivery.captured.status, name)
            assertEquals(event.captured.eventId, sourceEventId.captured, name)
            assertEquals(event.captured.aggregateId, delivery.captured.id, name)
        }
    }

    @Test
    fun `a failed send writes a FAILED snapshot and no event`() {
        for ((name, build) in useCases) {
            sendFails()
            val delivery = slot<NotificationDelivery>()
            io.mockk.clearMocks(eventPublisher, answers = false, recordedCalls = true)

            build()()

            verify(exactly = 0) { eventPublisher.publish(any()) }
            verify(exactly = 1) { eventPublisher.publishSnapshot(capture(delivery)) }
            assertEquals(NotificationStatus.FAILED, delivery.captured.status, name)
        }
    }

    @Test
    fun `a failed outbox write fails the use case instead of becoming a Failure result`() {
        for ((name, build) in useCases) {
            sendSucceeds()
            every { eventPublisher.publish(any()) } throws OutboxWriteException("outbox down", RuntimeException())

            val thrown = assertThrows<OutboxWriteException>(name) { build()() }

            assertTrue(thrown.message!!.contains("outbox down"), name)
        }
    }
}
