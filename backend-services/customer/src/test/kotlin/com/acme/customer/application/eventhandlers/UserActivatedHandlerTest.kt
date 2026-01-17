package com.acme.customer.application.eventhandlers

import arrow.core.left
import arrow.core.right
import com.acme.customer.application.ActivateCustomerError
import com.acme.customer.application.ActivateCustomerSuccess
import com.acme.customer.application.ActivateCustomerUseCase
import com.acme.customer.domain.Customer
import com.acme.customer.domain.CustomerStatus
import com.acme.customer.infrastructure.messaging.dto.ActivationMethod
import com.acme.customer.infrastructure.messaging.dto.UserActivatedEvent
import com.acme.customer.infrastructure.messaging.dto.UserActivatedPayload
import com.acme.customer.infrastructure.persistence.ProcessedEvent
import com.acme.customer.infrastructure.persistence.ProcessedEventRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class UserActivatedHandlerTest {

    private lateinit var activateCustomerUseCase: ActivateCustomerUseCase
    private lateinit var processedEventRepository: ProcessedEventRepository
    private lateinit var handler: UserActivatedHandler

    @BeforeEach
    fun setUp() {
        activateCustomerUseCase = mockk()
        processedEventRepository = mockk()

        handler = UserActivatedHandler(
            activateCustomerUseCase = activateCustomerUseCase,
            processedEventRepository = processedEventRepository
        )
    }

    private fun createUserActivatedEvent(
        eventId: UUID = UUID.randomUUID(),
        userId: UUID = UUID.randomUUID(),
        activatedAt: Instant = Instant.now(),
        correlationId: UUID = UUID.randomUUID()
    ): UserActivatedEvent {
        return UserActivatedEvent(
            eventId = eventId,
            eventType = UserActivatedEvent.EVENT_TYPE,
            eventVersion = "1.0",
            timestamp = Instant.now(),
            aggregateId = userId,
            aggregateType = "User",
            correlationId = correlationId,
            payload = UserActivatedPayload(
                userId = userId,
                activatedAt = activatedAt,
                activationMethod = ActivationMethod.EMAIL_VERIFICATION
            )
        )
    }

    @Test
    fun `handle should process event and call use case`() {
        // Given
        val eventId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val event = createUserActivatedEvent(eventId = eventId, userId = userId)

        val customer = mockk<Customer> {
            every { id } returns customerId
        }

        every { processedEventRepository.existsByEventId(eventId) } returns false
        every { activateCustomerUseCase.execute(any(), any(), any(), any()) } returns
            ActivateCustomerSuccess(customer).right()
        every { processedEventRepository.save(any()) } answers { firstArg() }

        // When
        handler.handle(event)

        // Then
        verify { activateCustomerUseCase.execute(userId, event.payload.activatedAt, event.correlationId, eventId) }
        verify { processedEventRepository.save(match { it.eventId == eventId }) }
    }

    @Test
    fun `handle should skip already processed events`() {
        // Given
        val eventId = UUID.randomUUID()
        val event = createUserActivatedEvent(eventId = eventId)

        every { processedEventRepository.existsByEventId(eventId) } returns true

        // When
        handler.handle(event)

        // Then
        verify(exactly = 0) { activateCustomerUseCase.execute(any(), any(), any(), any()) }
        verify(exactly = 0) { processedEventRepository.save(any()) }
    }

    @Test
    fun `handle should handle already active customer gracefully`() {
        // Given
        val eventId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val event = createUserActivatedEvent(eventId = eventId)

        every { processedEventRepository.existsByEventId(eventId) } returns false
        every { activateCustomerUseCase.execute(any(), any(), any(), any()) } returns
            ActivateCustomerError.AlreadyActive(customerId).left()
        every { processedEventRepository.save(any()) } answers { firstArg() }

        // When - handler should not throw, and should save event as processed
        handler.handle(event)

        // Then - event is saved as processed (handler completes without throwing)
        verify { processedEventRepository.save(match { it.eventId == eventId }) }
    }

    @Test
    fun `handle should throw when customer not found`() {
        // Given
        val eventId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val event = createUserActivatedEvent(eventId = eventId, userId = userId)

        every { processedEventRepository.existsByEventId(eventId) } returns false
        every { activateCustomerUseCase.execute(any(), any(), any(), any()) } returns
            ActivateCustomerError.CustomerNotFound(userId).left()

        // When/Then
        val exception = assertThrows<RuntimeException> {
            handler.handle(event)
        }

        assertEquals("Customer not found for user $userId", exception.message)
        verify(exactly = 0) { processedEventRepository.save(any()) }
    }

    @Test
    fun `handle should throw when use case fails`() {
        // Given
        val eventId = UUID.randomUUID()
        val event = createUserActivatedEvent(eventId = eventId)

        every { processedEventRepository.existsByEventId(eventId) } returns false
        every { activateCustomerUseCase.execute(any(), any(), any(), any()) } returns
            ActivateCustomerError.Failure("Database error", RuntimeException("Connection failed")).left()

        // When/Then
        val exception = assertThrows<RuntimeException> {
            handler.handle(event)
        }

        assertEquals("Database error", exception.message)
        verify(exactly = 0) { processedEventRepository.save(any()) }
    }

    @Test
    fun `handle should mark event as processed with correct event type`() {
        // Given
        val eventId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val event = createUserActivatedEvent(eventId = eventId, userId = userId)
        val processedEventSlot = slot<ProcessedEvent>()

        val customer = mockk<Customer> {
            every { id } returns customerId
        }

        every { processedEventRepository.existsByEventId(eventId) } returns false
        every { activateCustomerUseCase.execute(any(), any(), any(), any()) } returns
            ActivateCustomerSuccess(customer).right()
        every { processedEventRepository.save(capture(processedEventSlot)) } answers { firstArg() }

        // When
        handler.handle(event)

        // Then
        assertEquals(eventId, processedEventSlot.captured.eventId)
        assertEquals(UserActivatedEvent.EVENT_TYPE, processedEventSlot.captured.eventType)
    }
}
