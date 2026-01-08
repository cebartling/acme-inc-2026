package com.acme.notification.application.eventhandlers

import com.acme.notification.application.SendVerificationEmailResult
import com.acme.notification.application.SendVerificationEmailUseCase
import com.acme.notification.infrastructure.messaging.dto.UserRegisteredEvent
import com.acme.notification.infrastructure.messaging.dto.UserRegisteredPayload
import com.acme.notification.infrastructure.persistence.ProcessedEvent
import com.acme.notification.infrastructure.persistence.ProcessedEventRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class UserRegisteredHandlerTest {

    private lateinit var sendVerificationEmailUseCase: SendVerificationEmailUseCase
    private lateinit var processedEventRepository: ProcessedEventRepository
    private lateinit var handler: UserRegisteredHandler

    @BeforeEach
    fun setUp() {
        sendVerificationEmailUseCase = mockk()
        processedEventRepository = mockk()
        handler = UserRegisteredHandler(
            sendVerificationEmailUseCase = sendVerificationEmailUseCase,
            processedEventRepository = processedEventRepository
        )
    }

    @Test
    fun `should process event and send verification email`() {
        val event = createTestEvent()

        every { processedEventRepository.existsByEventId(event.eventId) } returns false
        every { sendVerificationEmailUseCase.execute(any(), any(), any(), any(), any()) } returns
                SendVerificationEmailResult.Success(UUID.randomUUID(), "sg-msg-123")
        every { processedEventRepository.save(any()) } answers { firstArg() }

        handler.handle(event)

        verify {
            sendVerificationEmailUseCase.execute(
                userId = event.payload.userId,
                email = event.payload.email,
                firstName = event.payload.firstName,
                verificationToken = event.payload.verificationToken!!,
                correlationId = event.correlationId
            )
        }
        verify { processedEventRepository.save(match { it.eventId == event.eventId }) }
    }

    @Test
    fun `should skip already processed events`() {
        val event = createTestEvent()

        every { processedEventRepository.existsByEventId(event.eventId) } returns true

        handler.handle(event)

        verify(exactly = 0) { sendVerificationEmailUseCase.execute(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { processedEventRepository.save(any()) }
    }

    @Test
    fun `should throw exception when verification token is missing`() {
        val event = createTestEvent(verificationToken = null)

        every { processedEventRepository.existsByEventId(event.eventId) } returns false

        val exception = assertThrows<IllegalStateException> {
            handler.handle(event)
        }

        assertTrue(exception.message?.contains("Missing verification token") == true)
        verify(exactly = 0) { sendVerificationEmailUseCase.execute(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should throw exception when email sending fails`() {
        val event = createTestEvent()

        every { processedEventRepository.existsByEventId(event.eventId) } returns false
        every { sendVerificationEmailUseCase.execute(any(), any(), any(), any(), any()) } returns
                SendVerificationEmailResult.Failure("Failed to send email")

        val exception = assertThrows<RuntimeException> {
            handler.handle(event)
        }

        assertTrue(exception.message?.contains("Failed to send email") == true)
        verify(exactly = 0) { processedEventRepository.save(any()) }
    }

    @Test
    fun `should handle AlreadySent result gracefully`() {
        val event = createTestEvent()

        every { processedEventRepository.existsByEventId(event.eventId) } returns false
        every { sendVerificationEmailUseCase.execute(any(), any(), any(), any(), any()) } returns
                SendVerificationEmailResult.AlreadySent(UUID.randomUUID())
        every { processedEventRepository.save(any()) } answers { firstArg() }

        handler.handle(event)

        verify { processedEventRepository.save(match { it.eventId == event.eventId }) }
    }

    private fun createTestEvent(verificationToken: String? = "test-token-123") = UserRegisteredEvent(
        eventId = UUID.randomUUID(),
        eventType = "UserRegistered",
        eventVersion = "1.0",
        timestamp = Instant.now(),
        aggregateId = UUID.randomUUID(),
        aggregateType = "User",
        correlationId = UUID.randomUUID(),
        payload = UserRegisteredPayload(
            userId = UUID.randomUUID(),
            email = "user@example.com",
            firstName = "John",
            lastName = "Doe",
            tosAcceptedAt = Instant.now(),
            marketingOptIn = false,
            registrationSource = "WEB",
            verificationToken = verificationToken
        )
    )
}
