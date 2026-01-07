package com.acme.notification.application

import com.acme.notification.domain.NotificationDelivery
import com.acme.notification.domain.NotificationType
import com.acme.notification.infrastructure.email.EmailSendResult
import com.acme.notification.infrastructure.email.SendGridEmailSender
import com.acme.notification.infrastructure.messaging.NotificationEventPublisher
import com.acme.notification.infrastructure.persistence.NotificationDeliveryRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

class SendVerificationEmailUseCaseTest {

    private lateinit var emailSender: SendGridEmailSender
    private lateinit var deliveryRepository: NotificationDeliveryRepository
    private lateinit var eventPublisher: NotificationEventPublisher
    private lateinit var useCase: SendVerificationEmailUseCase

    @BeforeEach
    fun setUp() {
        emailSender = mockk()
        deliveryRepository = mockk()
        eventPublisher = mockk(relaxed = true)
        useCase = SendVerificationEmailUseCase(
            emailSender = emailSender,
            deliveryRepository = deliveryRepository,
            eventPublisher = eventPublisher,
            meterRegistry = SimpleMeterRegistry()
        )
    }

    @Test
    fun `should send verification email successfully`() {
        val userId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val email = "user@example.com"
        val firstName = "John"
        val verificationToken = "test-token-123"

        every { deliveryRepository.existsByRecipientIdAndNotificationType(userId, NotificationType.EMAIL_VERIFICATION) } returns false
        every { deliveryRepository.save(any()) } answers { firstArg() }
        every { emailSender.sendVerificationEmail(email, firstName, verificationToken, correlationId.toString()) } returns
                EmailSendResult.Success("sg-msg-123", 202)

        val result = useCase.execute(
            userId = userId,
            email = email,
            firstName = firstName,
            verificationToken = verificationToken,
            correlationId = correlationId
        )

        assertTrue(result is SendVerificationEmailResult.Success)
        val success = result as SendVerificationEmailResult.Success
        assertEquals("sg-msg-123", success.providerMessageId)

        verify { deliveryRepository.save(match { it.status.name == "SENT" }) }
        verify { eventPublisher.publish(any()) }
    }

    @Test
    fun `should return AlreadySent when email was already sent for user`() {
        val userId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val existingDelivery = NotificationDelivery(
            id = UUID.randomUUID(),
            notificationType = NotificationType.EMAIL_VERIFICATION,
            recipientId = userId,
            recipientEmail = "user@example.com"
        )

        every { deliveryRepository.existsByRecipientIdAndNotificationType(userId, NotificationType.EMAIL_VERIFICATION) } returns true
        every { deliveryRepository.findByRecipientId(userId) } returns listOf(existingDelivery)

        val result = useCase.execute(
            userId = userId,
            email = "user@example.com",
            firstName = "John",
            verificationToken = "test-token",
            correlationId = correlationId
        )

        assertTrue(result is SendVerificationEmailResult.AlreadySent)

        verify(exactly = 0) { emailSender.sendVerificationEmail(any(), any(), any(), any()) }
        verify(exactly = 0) { eventPublisher.publish(any()) }
    }

    @Test
    fun `should return Failure when email sending fails`() {
        val userId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val email = "user@example.com"
        val firstName = "John"
        val verificationToken = "test-token"

        every { deliveryRepository.existsByRecipientIdAndNotificationType(userId, NotificationType.EMAIL_VERIFICATION) } returns false
        every { deliveryRepository.save(any()) } answers { firstArg() }
        every { emailSender.sendVerificationEmail(email, firstName, verificationToken, correlationId.toString()) } returns
                EmailSendResult.Failure("SendGrid returned 500", 500)

        val result = useCase.execute(
            userId = userId,
            email = email,
            firstName = firstName,
            verificationToken = verificationToken,
            correlationId = correlationId
        )

        assertTrue(result is SendVerificationEmailResult.Failure)
        val failure = result as SendVerificationEmailResult.Failure
        assertTrue(failure.message.contains("500"))

        verify { deliveryRepository.save(match { it.status.name == "FAILED" }) }
        verify(exactly = 0) { eventPublisher.publish(any()) }
    }

    @Test
    fun `should handle exception during email sending`() {
        val userId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()

        every { deliveryRepository.existsByRecipientIdAndNotificationType(userId, NotificationType.EMAIL_VERIFICATION) } returns false
        every { deliveryRepository.save(any()) } answers { firstArg() }
        every { emailSender.sendVerificationEmail(any(), any(), any(), any()) } throws RuntimeException("Network error")

        val result = useCase.execute(
            userId = userId,
            email = "user@example.com",
            firstName = "John",
            verificationToken = "test-token",
            correlationId = correlationId
        )

        assertTrue(result is SendVerificationEmailResult.Failure)
        val failure = result as SendVerificationEmailResult.Failure
        assertTrue(failure.message.contains("Network error"))
    }
}
