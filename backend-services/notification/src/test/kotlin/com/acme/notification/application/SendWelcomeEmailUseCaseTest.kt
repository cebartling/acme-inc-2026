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
import java.util.UUID

class SendWelcomeEmailUseCaseTest {

    private lateinit var emailSender: SendGridEmailSender
    private lateinit var deliveryRepository: NotificationDeliveryRepository
    private lateinit var eventPublisher: NotificationEventPublisher
    private lateinit var useCase: SendWelcomeEmailUseCase

    @BeforeEach
    fun setUp() {
        emailSender = mockk()
        deliveryRepository = mockk()
        eventPublisher = mockk(relaxed = true)
        useCase = SendWelcomeEmailUseCase(
            emailSender = emailSender,
            deliveryRepository = deliveryRepository,
            eventPublisher = eventPublisher,
            meterRegistry = SimpleMeterRegistry()
        )
    }

    @Test
    fun `should send welcome email successfully without marketing`() {
        val customerId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val email = "customer@example.com"
        val firstName = "Jane"
        val displayName = "Jane Doe"
        val customerNumber = "ACME-202601-000142"

        every { deliveryRepository.existsByRecipientIdAndNotificationType(customerId, NotificationType.WELCOME) } returns false
        every { deliveryRepository.save(any()) } answers { firstArg() }
        every {
            emailSender.sendWelcomeEmail(
                recipientEmail = email,
                recipientName = firstName,
                displayName = displayName,
                customerNumber = customerNumber,
                marketingOptIn = false,
                showProfileCta = true,
                correlationId = correlationId.toString()
            )
        } returns EmailSendResult.Success("sg-msg-123", 202)

        val result = useCase.execute(
            customerId = customerId,
            email = email,
            firstName = firstName,
            displayName = displayName,
            customerNumber = customerNumber,
            marketingOptIn = false,
            profileCompleteness = 80,
            correlationId = correlationId
        )

        assertTrue(result is SendWelcomeEmailResult.Success)
        val success = result as SendWelcomeEmailResult.Success
        assertEquals("sg-msg-123", success.providerMessageId)
        assertFalse(success.marketingIncluded)

        verify { deliveryRepository.save(match { it.status.name == "SENT" }) }
        verify { eventPublisher.publish(any()) }
    }

    @Test
    fun `should send welcome email successfully with marketing`() {
        val customerId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val email = "customer@example.com"
        val firstName = "John"
        val displayName = "John Smith"
        val customerNumber = "ACME-202601-000143"

        every { deliveryRepository.existsByRecipientIdAndNotificationType(customerId, NotificationType.WELCOME) } returns false
        every { deliveryRepository.save(any()) } answers { firstArg() }
        every {
            emailSender.sendWelcomeEmail(
                recipientEmail = email,
                recipientName = firstName,
                displayName = displayName,
                customerNumber = customerNumber,
                marketingOptIn = true,
                showProfileCta = false,
                correlationId = correlationId.toString()
            )
        } returns EmailSendResult.Success("sg-msg-456", 202)

        val result = useCase.execute(
            customerId = customerId,
            email = email,
            firstName = firstName,
            displayName = displayName,
            customerNumber = customerNumber,
            marketingOptIn = true,
            profileCompleteness = 100,
            correlationId = correlationId
        )

        assertTrue(result is SendWelcomeEmailResult.Success)
        val success = result as SendWelcomeEmailResult.Success
        assertEquals("sg-msg-456", success.providerMessageId)
        assertTrue(success.marketingIncluded)

        verify { deliveryRepository.save(match { it.status.name == "SENT" }) }
        verify { eventPublisher.publish(any()) }
    }

    @Test
    fun `should return AlreadySent when welcome email was already sent for customer`() {
        val customerId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val existingDelivery = NotificationDelivery(
            id = UUID.randomUUID(),
            notificationType = NotificationType.WELCOME,
            recipientId = customerId,
            recipientEmail = "customer@example.com"
        )

        every { deliveryRepository.existsByRecipientIdAndNotificationType(customerId, NotificationType.WELCOME) } returns true
        every { deliveryRepository.findByRecipientId(customerId) } returns listOf(existingDelivery)

        val result = useCase.execute(
            customerId = customerId,
            email = "customer@example.com",
            firstName = "Jane",
            displayName = "Jane Doe",
            customerNumber = "ACME-202601-000142",
            marketingOptIn = false,
            profileCompleteness = 50,
            correlationId = correlationId
        )

        assertTrue(result is SendWelcomeEmailResult.AlreadySent)

        verify(exactly = 0) { emailSender.sendWelcomeEmail(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { eventPublisher.publish(any()) }
    }

    @Test
    fun `should return Failure when email sending fails`() {
        val customerId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val email = "customer@example.com"
        val firstName = "Jane"
        val displayName = "Jane Doe"
        val customerNumber = "ACME-202601-000142"

        every { deliveryRepository.existsByRecipientIdAndNotificationType(customerId, NotificationType.WELCOME) } returns false
        every { deliveryRepository.save(any()) } answers { firstArg() }
        every {
            emailSender.sendWelcomeEmail(
                recipientEmail = email,
                recipientName = firstName,
                displayName = displayName,
                customerNumber = customerNumber,
                marketingOptIn = false,
                showProfileCta = true,
                correlationId = correlationId.toString()
            )
        } returns EmailSendResult.Failure("SendGrid returned 500", 500)

        val result = useCase.execute(
            customerId = customerId,
            email = email,
            firstName = firstName,
            displayName = displayName,
            customerNumber = customerNumber,
            marketingOptIn = false,
            profileCompleteness = 80,
            correlationId = correlationId
        )

        assertTrue(result is SendWelcomeEmailResult.Failure)
        val failure = result as SendWelcomeEmailResult.Failure
        assertTrue(failure.message.contains("500"))

        verify { deliveryRepository.save(match { it.status.name == "FAILED" }) }
        verify(exactly = 0) { eventPublisher.publish(any()) }
    }

    @Test
    fun `should handle exception during email sending`() {
        val customerId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()

        every { deliveryRepository.existsByRecipientIdAndNotificationType(customerId, NotificationType.WELCOME) } returns false
        every { deliveryRepository.save(any()) } answers { firstArg() }
        every { emailSender.sendWelcomeEmail(any(), any(), any(), any(), any(), any(), any()) } throws RuntimeException("Network error")

        val result = useCase.execute(
            customerId = customerId,
            email = "customer@example.com",
            firstName = "Jane",
            displayName = "Jane Doe",
            customerNumber = "ACME-202601-000142",
            marketingOptIn = false,
            profileCompleteness = 50,
            correlationId = correlationId
        )

        assertTrue(result is SendWelcomeEmailResult.Failure)
        val failure = result as SendWelcomeEmailResult.Failure
        assertTrue(failure.message.contains("Network error"))
    }

    @Test
    fun `should show profile CTA when profile completeness is less than 100`() {
        val customerId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()

        every { deliveryRepository.existsByRecipientIdAndNotificationType(customerId, NotificationType.WELCOME) } returns false
        every { deliveryRepository.save(any()) } answers { firstArg() }
        every {
            emailSender.sendWelcomeEmail(
                any(),
                any(),
                any(),
                any(),
                any(),
                showProfileCta = eq(true),
                any()
            )
        } returns EmailSendResult.Success("msg-123", 202)

        useCase.execute(
            customerId = customerId,
            email = "test@example.com",
            firstName = "Test",
            displayName = "Test User",
            customerNumber = "ACME-202601-000001",
            marketingOptIn = false,
            profileCompleteness = 50, // Less than 100
            correlationId = correlationId
        )

        verify {
            emailSender.sendWelcomeEmail(
                any(),
                any(),
                any(),
                any(),
                any(),
                showProfileCta = true,
                any()
            )
        }
    }

    @Test
    fun `should not show profile CTA when profile completeness is 100`() {
        val customerId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()

        every { deliveryRepository.existsByRecipientIdAndNotificationType(customerId, NotificationType.WELCOME) } returns false
        every { deliveryRepository.save(any()) } answers { firstArg() }
        every {
            emailSender.sendWelcomeEmail(
                any(),
                any(),
                any(),
                any(),
                any(),
                showProfileCta = eq(false),
                any()
            )
        } returns EmailSendResult.Success("msg-123", 202)

        useCase.execute(
            customerId = customerId,
            email = "test@example.com",
            firstName = "Test",
            displayName = "Test User",
            customerNumber = "ACME-202601-000001",
            marketingOptIn = false,
            profileCompleteness = 100, // Exactly 100
            correlationId = correlationId
        )

        verify {
            emailSender.sendWelcomeEmail(
                any(),
                any(),
                any(),
                any(),
                any(),
                showProfileCta = false,
                any()
            )
        }
    }
}
