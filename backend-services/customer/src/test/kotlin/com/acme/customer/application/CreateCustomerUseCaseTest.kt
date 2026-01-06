package com.acme.customer.application

import com.acme.customer.domain.Customer
import com.acme.customer.domain.CustomerPreferences
import com.acme.customer.infrastructure.messaging.CustomerEventPublisher
import com.acme.customer.infrastructure.persistence.CustomerNumberSequenceRepository
import com.acme.customer.infrastructure.persistence.CustomerPreferencesRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import com.acme.customer.infrastructure.persistence.EventStoreRepository
import com.acme.customer.infrastructure.projection.CustomerReadModelProjector
import com.acme.customer.infrastructure.security.CustomerIdGenerator
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreateCustomerUseCaseTest {

    private lateinit var customerRepository: CustomerRepository
    private lateinit var customerPreferencesRepository: CustomerPreferencesRepository
    private lateinit var customerNumberSequenceRepository: CustomerNumberSequenceRepository
    private lateinit var eventStoreRepository: EventStoreRepository
    private lateinit var customerIdGenerator: CustomerIdGenerator
    private lateinit var customerReadModelProjector: CustomerReadModelProjector
    private lateinit var customerEventPublisher: CustomerEventPublisher
    private lateinit var useCase: CreateCustomerUseCase

    @BeforeEach
    fun setUp() {
        customerRepository = mockk()
        customerPreferencesRepository = mockk()
        customerNumberSequenceRepository = mockk()
        eventStoreRepository = mockk()
        customerIdGenerator = mockk()
        customerReadModelProjector = mockk()
        customerEventPublisher = mockk()

        useCase = CreateCustomerUseCase(
            customerRepository = customerRepository,
            customerPreferencesRepository = customerPreferencesRepository,
            customerNumberSequenceRepository = customerNumberSequenceRepository,
            eventStoreRepository = eventStoreRepository,
            customerIdGenerator = customerIdGenerator,
            customerReadModelProjector = customerReadModelProjector,
            customerEventPublisher = customerEventPublisher,
            meterRegistry = SimpleMeterRegistry()
        )
    }

    @Test
    fun `execute should create customer successfully`() {
        // Given
        val userId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val email = "test@example.com"
        val firstName = "Jane"
        val lastName = "Doe"
        val marketingOptIn = true
        val registeredAt = Instant.now()
        val correlationId = UUID.randomUUID()
        val causationId = UUID.randomUUID()

        every { customerRepository.findByUserId(userId) } returns null
        every { customerIdGenerator.generate() } returns customerId
        every { customerNumberSequenceRepository.nextSequence(any()) } returns 1
        every { eventStoreRepository.append(any()) } just Runs
        every { customerRepository.save(any()) } answers { firstArg() }
        every { customerPreferencesRepository.save(any()) } answers { firstArg() }
        every { customerReadModelProjector.projectCustomer(any(), any()) } returns CompletableFuture.completedFuture(null)
        every { customerEventPublisher.publish(any()) } returns CompletableFuture.completedFuture(null)

        // When
        val result = useCase.execute(
            userId = userId,
            email = email,
            firstName = firstName,
            lastName = lastName,
            marketingOptIn = marketingOptIn,
            registeredAt = registeredAt,
            correlationId = correlationId,
            causationId = causationId
        )

        // Then
        assertTrue(result is CreateCustomerResult.Success)
        val success = result as CreateCustomerResult.Success
        assertEquals(customerId, success.customer.id)
        assertEquals(userId, success.customer.userId)
        assertEquals(email, success.customer.email)
        assertEquals(firstName, success.customer.firstName)
        assertEquals(lastName, success.customer.lastName)
        assertEquals("$firstName $lastName", success.customer.displayName)
        assertEquals(25, success.customer.profileCompleteness)
        assertTrue(success.preferences.marketingCommunications)

        // Verify interactions
        verify { eventStoreRepository.append(any()) }
        verify { customerRepository.save(any()) }
        verify { customerPreferencesRepository.save(any()) }
        verify { customerReadModelProjector.projectCustomer(any(), any()) }
        verify { customerEventPublisher.publish(any()) }
    }

    @Test
    fun `execute should return AlreadyExists when customer exists for user`() {
        // Given
        val userId = UUID.randomUUID()
        val existingCustomerId = UUID.randomUUID()
        val existingCustomer = mockk<Customer> {
            every { id } returns existingCustomerId
        }

        every { customerRepository.findByUserId(userId) } returns existingCustomer

        // When
        val result = useCase.execute(
            userId = userId,
            email = "test@example.com",
            firstName = "Jane",
            lastName = "Doe",
            marketingOptIn = false,
            registeredAt = Instant.now(),
            correlationId = UUID.randomUUID(),
            causationId = UUID.randomUUID()
        )

        // Then
        assertTrue(result is CreateCustomerResult.AlreadyExists)
        val alreadyExists = result as CreateCustomerResult.AlreadyExists
        assertEquals(userId, alreadyExists.userId)
        assertEquals(existingCustomerId, alreadyExists.existingCustomerId)

        // Verify no customer creation occurred
        verify(exactly = 0) { customerIdGenerator.generate() }
        verify(exactly = 0) { customerRepository.save(any()) }
    }

    @Test
    fun `execute should set marketingCommunications based on marketingOptIn`() {
        // Given
        val userId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val preferencesSlot = slot<CustomerPreferences>()

        every { customerRepository.findByUserId(userId) } returns null
        every { customerIdGenerator.generate() } returns customerId
        every { customerNumberSequenceRepository.nextSequence(any()) } returns 1
        every { eventStoreRepository.append(any()) } just Runs
        every { customerRepository.save(any()) } answers { firstArg() }
        every { customerPreferencesRepository.save(capture(preferencesSlot)) } answers { firstArg() }
        every { customerReadModelProjector.projectCustomer(any(), any()) } returns CompletableFuture.completedFuture(null)
        every { customerEventPublisher.publish(any()) } returns CompletableFuture.completedFuture(null)

        // When - with marketingOptIn = false
        useCase.execute(
            userId = userId,
            email = "test@example.com",
            firstName = "Jane",
            lastName = "Doe",
            marketingOptIn = false,
            registeredAt = Instant.now(),
            correlationId = UUID.randomUUID(),
            causationId = UUID.randomUUID()
        )

        // Then
        assertEquals(false, preferencesSlot.captured.marketingCommunications)
    }

    @Test
    fun `execute should generate customer number with correct format`() {
        // Given
        val userId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val customerSlot = slot<Customer>()
        val yearMonth = YearMonth.now()
        val expectedYearMonthStr = yearMonth.toString().replace("-", "")

        every { customerRepository.findByUserId(userId) } returns null
        every { customerIdGenerator.generate() } returns customerId
        every { customerNumberSequenceRepository.nextSequence(yearMonth) } returns 142
        every { eventStoreRepository.append(any()) } just Runs
        every { customerRepository.save(capture(customerSlot)) } answers { firstArg() }
        every { customerPreferencesRepository.save(any()) } answers { firstArg() }
        every { customerReadModelProjector.projectCustomer(any(), any()) } returns CompletableFuture.completedFuture(null)
        every { customerEventPublisher.publish(any()) } returns CompletableFuture.completedFuture(null)

        // When
        useCase.execute(
            userId = userId,
            email = "test@example.com",
            firstName = "Jane",
            lastName = "Doe",
            marketingOptIn = true,
            registeredAt = Instant.now(),
            correlationId = UUID.randomUUID(),
            causationId = UUID.randomUUID()
        )

        // Then
        assertEquals("ACME-$expectedYearMonthStr-000142", customerSlot.captured.customerNumber)
    }

    @Test
    fun `execute should return Failure when exception occurs`() {
        // Given
        val userId = UUID.randomUUID()

        every { customerRepository.findByUserId(userId) } returns null
        every { customerIdGenerator.generate() } throws RuntimeException("Database error")

        // When
        val result = useCase.execute(
            userId = userId,
            email = "test@example.com",
            firstName = "Jane",
            lastName = "Doe",
            marketingOptIn = true,
            registeredAt = Instant.now(),
            correlationId = UUID.randomUUID(),
            causationId = UUID.randomUUID()
        )

        // Then
        assertTrue(result is CreateCustomerResult.Failure)
        val failure = result as CreateCustomerResult.Failure
        assertTrue(failure.message.contains("Database error"))
    }

    @Test
    fun `execute should return Failure when MongoDB projection fails`() {
        // Given
        val userId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val email = "test@example.com"
        val firstName = "Jane"
        val lastName = "Doe"
        val marketingOptIn = true
        val registeredAt = Instant.now()
        val correlationId = UUID.randomUUID()
        val causationId = UUID.randomUUID()

        every { customerRepository.findByUserId(userId) } returns null
        every { customerIdGenerator.generate() } returns customerId
        every { customerNumberSequenceRepository.nextSequence(any()) } returns 1
        every { eventStoreRepository.append(any()) } just Runs
        every { customerRepository.save(any()) } answers { firstArg() }
        every { customerPreferencesRepository.save(any()) } answers { firstArg() }
        
        // MongoDB projection fails
        every { customerReadModelProjector.projectCustomer(any(), any()) } returns 
            CompletableFuture.failedFuture(RuntimeException("MongoDB connection failed"))
        
        every { customerEventPublisher.publish(any()) } returns CompletableFuture.completedFuture(null)

        // When
        val result = useCase.execute(
            userId = userId,
            email = email,
            firstName = firstName,
            lastName = lastName,
            marketingOptIn = marketingOptIn,
            registeredAt = registeredAt,
            correlationId = correlationId,
            causationId = causationId
        )

        // Then - transaction should fail and return Failure result
        assertTrue(result is CreateCustomerResult.Failure)
        val failure = result as CreateCustomerResult.Failure
        assertTrue(failure.message.contains("MongoDB connection failed"))
    }

    @Test
    fun `execute should return Failure when Kafka publishing fails`() {
        // Given
        val userId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val email = "test@example.com"
        val firstName = "Jane"
        val lastName = "Doe"
        val marketingOptIn = true
        val registeredAt = Instant.now()
        val correlationId = UUID.randomUUID()
        val causationId = UUID.randomUUID()

        every { customerRepository.findByUserId(userId) } returns null
        every { customerIdGenerator.generate() } returns customerId
        every { customerNumberSequenceRepository.nextSequence(any()) } returns 1
        every { eventStoreRepository.append(any()) } just Runs
        every { customerRepository.save(any()) } answers { firstArg() }
        every { customerPreferencesRepository.save(any()) } answers { firstArg() }
        every { customerReadModelProjector.projectCustomer(any(), any()) } returns CompletableFuture.completedFuture(null)
        
        // Kafka publishing fails
        every { customerEventPublisher.publish(any()) } returns 
            CompletableFuture.failedFuture(RuntimeException("Kafka broker unavailable"))

        // When
        val result = useCase.execute(
            userId = userId,
            email = email,
            firstName = firstName,
            lastName = lastName,
            marketingOptIn = marketingOptIn,
            registeredAt = registeredAt,
            correlationId = correlationId,
            causationId = causationId
        )

        // Then - transaction should fail and return Failure result
        assertTrue(result is CreateCustomerResult.Failure)
        val failure = result as CreateCustomerResult.Failure
        assertTrue(failure.message.contains("Kafka broker unavailable"))
    }
}
