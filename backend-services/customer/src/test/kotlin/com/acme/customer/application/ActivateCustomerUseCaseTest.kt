package com.acme.customer.application

import arrow.core.getOrElse
import com.acme.customer.domain.Customer
import com.acme.customer.domain.CustomerPreferences
import com.acme.customer.domain.CustomerStatus
import com.acme.customer.infrastructure.messaging.OutboxWriter
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
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class ActivateCustomerUseCaseTest {

    private lateinit var customerRepository: CustomerRepository
    private lateinit var customerPreferencesRepository: CustomerPreferencesRepository
    private lateinit var eventStoreRepository: EventStoreRepository
    private lateinit var customerReadModelProjector: CustomerReadModelProjector
    private lateinit var outboxWriter: OutboxWriter
    private lateinit var profileCompletionService: ProfileCompletionService
    private lateinit var useCase: ActivateCustomerUseCase

    @BeforeEach
    fun setUp() {
        customerRepository = mockk()
        customerPreferencesRepository = mockk()
        eventStoreRepository = mockk()
        customerReadModelProjector = mockk()
        outboxWriter = mockk()
        profileCompletionService = mockk()

        every { profileCompletionService.checkAndUpdateCompletion(any(), any(), any(), any()) } returns null

        useCase = ActivateCustomerUseCase(
            customerRepository = customerRepository,
            customerPreferencesRepository = customerPreferencesRepository,
            eventStoreRepository = eventStoreRepository,
            customerReadModelProjector = customerReadModelProjector,
            outboxWriter = outboxWriter,
            profileCompletionService = profileCompletionService,
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
        every { outboxWriter.write(any(), any()) } just Runs

        // When
        val result = useCase.execute(
            userId = userId,
            activatedAt = activatedAt,
            correlationId = correlationId,
            causationId = causationId
        )

        // Then
        assertTrue(result.isRight())
        val success = result.getOrElse { fail("Expected success but got error") }
        assertEquals(customerId, success.customer.id)
        assertEquals(CustomerStatus.ACTIVE, success.customer.status)
        assertTrue(success.customer.emailVerified)

        // Verify interactions
        verify { eventStoreRepository.append(any()) }
        verify { customerRepository.save(any()) }
        verify { customerReadModelProjector.projectCustomer(any(), any()) }
        verify { outboxWriter.write(any(), any()) }
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
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertIs<ActivateCustomerError.AlreadyActive>(error)
                assertEquals(customerId, error.customerId)
            },
            ifRight = { fail("Expected error but got success") }
        )

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
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertIs<ActivateCustomerError.CustomerNotFound>(error)
                assertEquals(userId, error.userId)
            },
            ifRight = { fail("Expected error but got success") }
        )

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
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertIs<ActivateCustomerError.Failure>(error)
                assertTrue(error.message.contains("MongoDB connection failed"))
            },
            ifRight = { fail("Expected error but got success") }
        )
    }

    @Test
    fun `execute should return Failure when outbox write fails`() {
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
        every { outboxWriter.write(any(), any()) } throws
            RuntimeException("Database constraint violation")

        // When
        val result = useCase.execute(
            userId = userId,
            activatedAt = activatedAt,
            correlationId = UUID.randomUUID(),
            causationId = UUID.randomUUID()
        )

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertIs<ActivateCustomerError.Failure>(error)
                assertTrue(error.message.contains("Database constraint violation"))
            },
            ifRight = { fail("Expected error but got success") }
        )
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
        every { outboxWriter.write(any(), any()) } just Runs

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

    @Test
    fun `execute should return Failure when customer preferences are not found`() {
        // Given
        val userId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val activatedAt = Instant.now()

        val customer = Customer.createFromRegistration(
            id = customerId,
            userId = userId,
            customerNumber = "ACME-202601-000006",
            email = "test@example.com",
            firstName = "Jane",
            lastName = "Doe",
            registeredAt = Instant.now().minusSeconds(3600)
        )

        every { customerRepository.findByUserId(userId) } returns customer
        every { eventStoreRepository.append(any()) } just Runs
        every { customerRepository.save(any()) } answers { firstArg() }
        every { customerPreferencesRepository.findById(customerId) } returns Optional.empty()

        // When
        val result = useCase.execute(
            userId = userId,
            activatedAt = activatedAt,
            correlationId = UUID.randomUUID(),
            causationId = UUID.randomUUID()
        )

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertIs<ActivateCustomerError.Failure>(error)
                assertTrue(error.message.contains("Cannot activate customer"))
                assertTrue(error.message.contains("Customer preferences not found for $customerId"))
            },
            ifRight = { fail("Expected error but got success") }
        )

        // Verify that event and customer were saved before preferences check
        verify { eventStoreRepository.append(any()) }
        verify { customerRepository.save(any()) }
        // Verify projection and publish were not called due to exception
        verify(exactly = 0) { customerReadModelProjector.projectCustomer(any(), any()) }
        verify(exactly = 0) { outboxWriter.write(any(), any()) }
    }
}
