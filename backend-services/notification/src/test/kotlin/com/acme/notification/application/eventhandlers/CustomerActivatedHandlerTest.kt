package com.acme.notification.application.eventhandlers

import com.acme.notification.application.SendWelcomeEmailResult
import com.acme.notification.application.SendWelcomeEmailUseCase
import com.acme.notification.infrastructure.client.CustomerQueryResult
import com.acme.notification.infrastructure.client.CustomerServiceClient
import com.acme.notification.infrastructure.client.dto.*
import com.acme.notification.infrastructure.messaging.dto.CustomerActivatedEvent
import com.acme.notification.infrastructure.messaging.dto.CustomerActivatedPayload
import com.acme.notification.infrastructure.persistence.ProcessedEventRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class CustomerActivatedHandlerTest {

    private lateinit var sendWelcomeEmailUseCase: SendWelcomeEmailUseCase
    private lateinit var customerServiceClient: CustomerServiceClient
    private lateinit var processedEventRepository: ProcessedEventRepository
    private lateinit var handler: CustomerActivatedHandler

    @BeforeEach
    fun setUp() {
        sendWelcomeEmailUseCase = mockk()
        customerServiceClient = mockk()
        processedEventRepository = mockk()
        handler = CustomerActivatedHandler(
            sendWelcomeEmailUseCase = sendWelcomeEmailUseCase,
            customerServiceClient = customerServiceClient,
            processedEventRepository = processedEventRepository
        )
    }

    @Test
    fun `should handle CustomerActivated event successfully`() {
        val customerId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val causationId = UUID.randomUUID()
        val eventId = UUID.randomUUID()

        val event = createEvent(
            eventId = eventId,
            customerId = customerId,
            correlationId = correlationId,
            causationId = causationId
        )

        val customer = createCustomerDto(
            customerId = customerId,
            email = "jane@example.com",
            firstName = "Jane",
            lastName = "Doe",
            marketingOptIn = false,
            profileCompleteness = 80
        )

        every { processedEventRepository.existsByEventId(eventId) } returns false
        every { customerServiceClient.getCustomerById(customerId) } returns CustomerQueryResult.Success(customer)
        every {
            sendWelcomeEmailUseCase.execute(
                customerId = customerId,
                email = "jane@example.com",
                firstName = "Jane",
                displayName = "Jane Doe",
                customerNumber = "ACME-202601-000142",
                marketingOptIn = false,
                profileCompleteness = 80,
                correlationId = correlationId
            )
        } returns SendWelcomeEmailResult.Success(
            notificationId = UUID.randomUUID(),
            providerMessageId = "msg-123",
            marketingIncluded = false
        )
        every { processedEventRepository.save(any()) } answers { firstArg() }

        handler.handle(event)

        verify { processedEventRepository.save(match { it.eventId == eventId }) }
    }

    @Test
    fun `should handle CustomerActivated event with marketing opt-in`() {
        val customerId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val causationId = UUID.randomUUID()
        val eventId = UUID.randomUUID()

        val event = createEvent(
            eventId = eventId,
            customerId = customerId,
            correlationId = correlationId,
            causationId = causationId
        )

        val customer = createCustomerDto(
            customerId = customerId,
            email = "john@example.com",
            firstName = "John",
            lastName = "Smith",
            marketingOptIn = true,
            profileCompleteness = 100
        )

        every { processedEventRepository.existsByEventId(eventId) } returns false
        every { customerServiceClient.getCustomerById(customerId) } returns CustomerQueryResult.Success(customer)
        every {
            sendWelcomeEmailUseCase.execute(
                customerId = customerId,
                email = "john@example.com",
                firstName = "John",
                displayName = "John Smith",
                customerNumber = "ACME-202601-000142",
                marketingOptIn = true,
                profileCompleteness = 100,
                correlationId = correlationId
            )
        } returns SendWelcomeEmailResult.Success(
            notificationId = UUID.randomUUID(),
            providerMessageId = "msg-456",
            marketingIncluded = true
        )
        every { processedEventRepository.save(any()) } answers { firstArg() }

        handler.handle(event)

        verify {
            sendWelcomeEmailUseCase.execute(
                any(),
                any(),
                any(),
                any(),
                any(),
                marketingOptIn = true,
                any(),
                any()
            )
        }
    }

    @Test
    fun `should skip processing when event was already processed`() {
        val eventId = UUID.randomUUID()
        val event = createEvent(eventId = eventId)

        every { processedEventRepository.existsByEventId(eventId) } returns true

        handler.handle(event)

        verify(exactly = 0) { customerServiceClient.getCustomerById(any()) }
        verify(exactly = 0) { sendWelcomeEmailUseCase.execute(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should throw exception when customer not found`() {
        val customerId = UUID.randomUUID()
        val eventId = UUID.randomUUID()
        val event = createEvent(eventId = eventId, customerId = customerId)

        every { processedEventRepository.existsByEventId(eventId) } returns false
        every { customerServiceClient.getCustomerById(customerId) } returns CustomerQueryResult.NotFound

        assertThrows<IllegalStateException> {
            handler.handle(event)
        }

        verify(exactly = 0) { sendWelcomeEmailUseCase.execute(any(), any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { processedEventRepository.save(any()) }
    }

    @Test
    fun `should throw exception when customer service returns error`() {
        val customerId = UUID.randomUUID()
        val eventId = UUID.randomUUID()
        val event = createEvent(eventId = eventId, customerId = customerId)

        every { processedEventRepository.existsByEventId(eventId) } returns false
        every { customerServiceClient.getCustomerById(customerId) } returns CustomerQueryResult.Error("Connection timeout")

        assertThrows<RuntimeException> {
            handler.handle(event)
        }

        verify(exactly = 0) { sendWelcomeEmailUseCase.execute(any(), any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { processedEventRepository.save(any()) }
    }

    @Test
    fun `should throw exception when email sending fails`() {
        val customerId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val eventId = UUID.randomUUID()

        val event = createEvent(
            eventId = eventId,
            customerId = customerId,
            correlationId = correlationId
        )

        val customer = createCustomerDto(customerId = customerId)

        every { processedEventRepository.existsByEventId(eventId) } returns false
        every { customerServiceClient.getCustomerById(customerId) } returns CustomerQueryResult.Success(customer)
        every {
            sendWelcomeEmailUseCase.execute(any(), any(), any(), any(), any(), any(), any(), any())
        } returns SendWelcomeEmailResult.Failure("SendGrid error")

        assertThrows<RuntimeException> {
            handler.handle(event)
        }

        verify(exactly = 0) { processedEventRepository.save(any()) }
    }

    @Test
    fun `should handle AlreadySent result without throwing`() {
        val customerId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val eventId = UUID.randomUUID()
        val existingNotificationId = UUID.randomUUID()

        val event = createEvent(
            eventId = eventId,
            customerId = customerId,
            correlationId = correlationId
        )

        val customer = createCustomerDto(customerId = customerId)

        every { processedEventRepository.existsByEventId(eventId) } returns false
        every { customerServiceClient.getCustomerById(customerId) } returns CustomerQueryResult.Success(customer)
        every {
            sendWelcomeEmailUseCase.execute(any(), any(), any(), any(), any(), any(), any(), any())
        } returns SendWelcomeEmailResult.AlreadySent(existingNotificationId)
        every { processedEventRepository.save(any()) } answers { firstArg() }

        handler.handle(event)

        verify { processedEventRepository.save(match { it.eventId == eventId }) }
    }

    private fun createEvent(
        eventId: UUID = UUID.randomUUID(),
        customerId: UUID = UUID.randomUUID(),
        correlationId: UUID = UUID.randomUUID(),
        causationId: UUID = UUID.randomUUID()
    ): CustomerActivatedEvent {
        return CustomerActivatedEvent(
            eventId = eventId,
            eventType = "CustomerActivated",
            eventVersion = "1.0",
            timestamp = Instant.now(),
            aggregateId = customerId,
            aggregateType = "Customer",
            correlationId = correlationId,
            causationId = causationId,
            payload = CustomerActivatedPayload(
                customerId = customerId,
                activatedAt = Instant.now(),
                emailVerified = true
            )
        )
    }

    private fun createCustomerDto(
        customerId: UUID = UUID.randomUUID(),
        email: String = "customer@example.com",
        firstName: String = "Test",
        lastName: String = "Customer",
        marketingOptIn: Boolean = false,
        profileCompleteness: Int = 50
    ): CustomerDto {
        return CustomerDto(
            customerId = customerId.toString(),
            userId = UUID.randomUUID().toString(),
            customerNumber = "ACME-202601-000142",
            name = NameDto(
                firstName = firstName,
                lastName = lastName,
                displayName = "$firstName $lastName"
            ),
            email = EmailDto(
                address = email,
                verified = true
            ),
            status = "ACTIVE",
            preferences = PreferencesDto(
                communication = CommunicationPreferencesDto(
                    email = true,
                    sms = false,
                    push = false,
                    marketing = marketingOptIn
                )
            ),
            profileCompleteness = profileCompleteness,
            registeredAt = Instant.now()
        )
    }
}
