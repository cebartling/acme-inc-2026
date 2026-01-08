package com.acme.customer.application

import com.acme.customer.domain.Customer
import com.acme.customer.domain.CustomerPreferences
import com.acme.customer.domain.CustomerStatus
import com.acme.customer.infrastructure.messaging.CustomerEventPublisher
import com.acme.customer.infrastructure.persistence.CustomerPreferencesRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import com.acme.customer.infrastructure.persistence.EventStoreRepository
import com.acme.customer.infrastructure.projection.CustomerReadModelProjector
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActivateCustomerUseCaseTest {

    private lateinit var customerRepository: CustomerRepository
    private lateinit var customerPreferencesRepository: CustomerPreferencesRepository
    private lateinit var eventStoreRepository: EventStoreRepository
    private lateinit var customerReadModelProjector: CustomerReadModelProjector
    private lateinit var customerEventPublisher: CustomerEventPublisher
    private lateinit var useCase: ActivateCustomerUseCase

    @BeforeEach
    fun setUp() {
        customerRepository = mockk()
        customerPreferencesRepository = mockk()
        eventStoreRepository = mockk()
        customerReadModelProjector = mockk()
        customerEventPublisher = mockk()

        useCase = ActivateCustomerUseCase(
            customerRepository = customerRepository,
            customerPreferencesRepository = customerPreferencesRepository,
            eventStoreRepository = eventStoreRepository,
            customerReadModelProjector = customerReadModelProjector,
            customerEventPublisher = customerEventPublisher,
            meterRegistry = SimpleMeterRegistry()
        )
    }

    @Test
    fun `execute should activate customer successfully`() {
        // Given
        val userId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val activatedAt = Instant.now()
        val correlationId = UUID.randomUUID()
        val causationId = UUID.randomUUID()

        val customer = Customer.createFromRegistration(
            id = customerId,
            userId = userId,
            customerNumber = "ACME-202601-000001",
            email = "test@example.com",
            firstName = "Jane",
            lastName = "Doe",
            registeredAt = Instant.now().minusSeconds(3600)
        )

        val preferences = CustomerPreferences.createDefault(
            customerId = customerId,
            marketingOptIn = false
        )

        every { customerRepository.findByUserId(userId) } returns customer
        every { eventStoreRepository.append(any()) } just Runs
        every { customerRepository.save(any()) } answers { firstArg() }
        every { customerPreferencesRepository.findById(customerId) } returns Optional.of(preferences)
        every { customerReadModelProjector.projectCustomer(any(), any()) } returns CompletableFuture.completedFuture(null)
        every { customerEventPublisher.publish(any<com.acme.customer.domain.events.CustomerActivated>()) } just Runs

        // When
        val result = useCase.execute(
            userId = userId,
            activatedAt = activatedAt,
            correlationId = correlationId,
            causationId = causationId
        )

        // Then
        assertTrue(result is ActivateCustomerResult.Success)
        val success = result as ActivateCustomerResult.Success
        assertEquals(customerId, success.customer.id)
        assertEquals(CustomerStatus.ACTIVE, success.customer.status)
        assertTrue(success.customer.emailVerified)

        // Verify interactions
        verify { eventStoreRepository.append(any()) }
        verify { customerRepository.save(any()) }
        verify { customerReadModelProjector.projectCustomer(any(), any()) }
        verify { customerEventPublisher.publish(any<com.acme.customer.domain.events.CustomerActivated>()) }
    }

    @Test
    fun `execute should return AlreadyActive when customer is already active`() {
        // Given
        val userId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val activatedAt = Instant.now()

        val customer = Customer.createFromRegistration(
            id = customerId,
            userId = userId,
            customerNumber = "ACME-202601-000002",
            email = "test@example.com",
            firstName = "Jane",
            lastName = "Doe",
            registeredAt = Instant.now().minusSeconds(3600)
        )
        // Manually set the customer to ACTIVE status
        customer.setActiveForTesting()
        customer.emailVerified = true

        every { customerRepository.findByUserId(userId) } returns customer

        // When
        val result = useCase.execute(
            userId = userId,
            activatedAt = activatedAt,
            correlationId = UUID.randomUUID(),
            causationId = UUID.randomUUID()
        )

        // Then
        assertTrue(result is ActivateCustomerResult.AlreadyActive)
        val alreadyActive = result as ActivateCustomerResult.AlreadyActive
        assertEquals(customerId, alreadyActive.customerId)

        // Verify no activation occurred
        verify(exactly = 0) { eventStoreRepository.append(any()) }
        verify(exactly = 0) { customerRepository.save(any()) }
    }

    @Test
    fun `execute should return CustomerNotFound when customer does not exist`() {
        // Given
        val userId = UUID.randomUUID()
        val activatedAt = Instant.now()

        every { customerRepository.findByUserId(userId) } returns null

        // When
        val result = useCase.execute(
            userId = userId,
            activatedAt = activatedAt,
            correlationId = UUID.randomUUID(),
            causationId = UUID.randomUUID()
        )

        // Then
        assertTrue(result is ActivateCustomerResult.CustomerNotFound)
        val notFound = result as ActivateCustomerResult.CustomerNotFound
        assertEquals(userId, notFound.userId)

        // Verify no activation occurred
        verify(exactly = 0) { eventStoreRepository.append(any()) }
        verify(exactly = 0) { customerRepository.save(any()) }
    }

    @Test
    fun `execute should return Failure when MongoDB projection fails`() {
        // Given
        val userId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val activatedAt = Instant.now()

        val customer = Customer.createFromRegistration(
            id = customerId,
            userId = userId,
            customerNumber = "ACME-202601-000003",
            email = "test@example.com",
            firstName = "Jane",
            lastName = "Doe",
            registeredAt = Instant.now().minusSeconds(3600)
        )

        val preferences = CustomerPreferences.createDefault(
            customerId = customerId,
            marketingOptIn = false
        )

        every { customerRepository.findByUserId(userId) } returns customer
        every { eventStoreRepository.append(any()) } just Runs
        every { customerRepository.save(any()) } answers { firstArg() }
        every { customerPreferencesRepository.findById(customerId) } returns Optional.of(preferences)
        every { customerReadModelProjector.projectCustomer(any(), any()) } returns
            CompletableFuture.failedFuture(RuntimeException("MongoDB connection failed"))

        // When
        val result = useCase.execute(
            userId = userId,
            activatedAt = activatedAt,
            correlationId = UUID.randomUUID(),
            causationId = UUID.randomUUID()
        )

        // Then
        assertTrue(result is ActivateCustomerResult.Failure)
        val failure = result as ActivateCustomerResult.Failure
        assertTrue(failure.message.contains("MongoDB connection failed"))
    }

    @Test
    fun `execute should return Failure when Kafka publishing fails`() {
        // Given
        val userId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val activatedAt = Instant.now()

        val customer = Customer.createFromRegistration(
            id = customerId,
            userId = userId,
            customerNumber = "ACME-202601-000004",
            email = "test@example.com",
            firstName = "Jane",
            lastName = "Doe",
            registeredAt = Instant.now().minusSeconds(3600)
        )

        val preferences = CustomerPreferences.createDefault(
            customerId = customerId,
            marketingOptIn = false
        )

        every { customerRepository.findByUserId(userId) } returns customer
        every { eventStoreRepository.append(any()) } just Runs
        every { customerRepository.save(any()) } answers { firstArg() }
        every { customerPreferencesRepository.findById(customerId) } returns Optional.of(preferences)
        every { customerReadModelProjector.projectCustomer(any(), any()) } returns CompletableFuture.completedFuture(null)
        every { customerEventPublisher.publish(any<com.acme.customer.domain.events.CustomerActivated>()) } throws
            RuntimeException("Kafka broker unavailable")

        // When
        val result = useCase.execute(
            userId = userId,
            activatedAt = activatedAt,
            correlationId = UUID.randomUUID(),
            causationId = UUID.randomUUID()
        )

        // Then
        assertTrue(result is ActivateCustomerResult.Failure)
        val failure = result as ActivateCustomerResult.Failure
        assertTrue(failure.message.contains("Kafka broker unavailable"))
    }

    @Test
    fun `execute should update lastActivityAt timestamp on activation`() {
        // Given
        val userId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val activatedAt = Instant.now()
        val customerSlot = slot<Customer>()

        val customer = Customer.createFromRegistration(
            id = customerId,
            userId = userId,
            customerNumber = "ACME-202601-000005",
            email = "test@example.com",
            firstName = "Jane",
            lastName = "Doe",
            registeredAt = Instant.now().minusSeconds(3600)
        )

        val preferences = CustomerPreferences.createDefault(
            customerId = customerId,
            marketingOptIn = false
        )

        every { customerRepository.findByUserId(userId) } returns customer
        every { eventStoreRepository.append(any()) } just Runs
        every { customerRepository.save(capture(customerSlot)) } answers { firstArg() }
        every { customerPreferencesRepository.findById(customerId) } returns Optional.of(preferences)
        every { customerReadModelProjector.projectCustomer(any(), any()) } returns CompletableFuture.completedFuture(null)
        every { customerEventPublisher.publish(any<com.acme.customer.domain.events.CustomerActivated>()) } just Runs

        // When
        useCase.execute(
            userId = userId,
            activatedAt = activatedAt,
            correlationId = UUID.randomUUID(),
            causationId = UUID.randomUUID()
        )

        // Then
        assertEquals(activatedAt, customerSlot.captured.lastActivityAt)
    }
}
