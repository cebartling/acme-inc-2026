package com.acme.customer.application

import com.acme.customer.domain.Customer
import com.acme.customer.domain.CustomerStatus
import com.acme.customer.domain.CustomerType
import com.acme.customer.domain.ProfileCompleteness
import com.acme.customer.domain.SectionCompleteness
import com.acme.customer.domain.events.ProfileCompleted
import com.acme.customer.infrastructure.messaging.OutboxWriter
import com.acme.customer.infrastructure.persistence.CustomerRepository
import com.acme.customer.infrastructure.persistence.EventStoreRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileCompletionServiceTest {

    private lateinit var profileCompletenessCalculator: ProfileCompletenessCalculator
    private lateinit var customerRepository: CustomerRepository
    private lateinit var eventStoreRepository: EventStoreRepository
    private lateinit var outboxWriter: OutboxWriter
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var service: ProfileCompletionService

    private val customerId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        profileCompletenessCalculator = mockk()
        customerRepository = mockk()
        eventStoreRepository = mockk()
        outboxWriter = mockk()
        meterRegistry = SimpleMeterRegistry()

        service = ProfileCompletionService(
            profileCompletenessCalculator = profileCompletenessCalculator,
            customerRepository = customerRepository,
            eventStoreRepository = eventStoreRepository,
            outboxWriter = outboxWriter,
            meterRegistry = meterRegistry
        )
    }

    private fun createCustomer(completeness: Int = 25): Customer {
        val registeredAt = Instant.now().minus(7, ChronoUnit.DAYS)
        return Customer(
            id = customerId,
            userId = userId,
            customerNumber = "ACME-202601-000001",
            email = "test@example.com",
            firstName = "John",
            lastName = "Doe",
            displayName = "John Doe",
            status = CustomerStatus.ACTIVE,
            type = CustomerType.INDIVIDUAL,
            emailVerified = true,
            phoneVerified = true,
            registeredAt = registeredAt,
            lastActivityAt = Instant.now(),
            profileCompleteness = completeness
        )
    }

    private fun createCompleteness(score: Int): ProfileCompleteness {
        return ProfileCompleteness(
            customerId = customerId,
            overallScore = score,
            sections = listOf(
                SectionCompleteness(
                    name = "basicInfo",
                    displayName = "Basic Info",
                    weight = 25,
                    score = 100,
                    isComplete = true,
                    items = emptyList()
                )
            ),
            nextAction = null
        )
    }

    @Nested
    inner class CheckAndUpdateCompletion {

        @Test
        fun `should return null when customer not found`() {
            every { customerRepository.findById(customerId) } returns Optional.empty()
            val correlationId = UUID.randomUUID()

            val result = service.checkAndUpdateCompletion(
                customerId = customerId,
                previousScore = 50,
                correlationId = correlationId
            )

            assertNull(result)
            verify(exactly = 0) { profileCompletenessCalculator.calculate(any()) }
        }

        @Test
        fun `should calculate completeness for found customer`() {
            val customer = createCustomer()
            val completeness = createCompleteness(50)
            every { customerRepository.findById(customerId) } returns Optional.of(customer)
            every { profileCompletenessCalculator.calculate(customer) } returns completeness
            every { customerRepository.save(any()) } returns customer

            val correlationId = UUID.randomUUID()

            val result = service.checkAndUpdateCompletion(
                customerId = customerId,
                previousScore = 25,
                correlationId = correlationId
            )

            assertNotNull(result)
            assertEquals(50, result.overallScore)
            verify { profileCompletenessCalculator.calculate(customer) }
        }

        @Test
        fun `should update customer when completeness score changes`() {
            val customer = createCustomer(25)
            val completeness = createCompleteness(50)
            val customerSlot = slot<Customer>()

            every { customerRepository.findById(customerId) } returns Optional.of(customer)
            every { profileCompletenessCalculator.calculate(customer) } returns completeness
            every { customerRepository.save(capture(customerSlot)) } returns customer

            val correlationId = UUID.randomUUID()

            service.checkAndUpdateCompletion(
                customerId = customerId,
                previousScore = 25,
                correlationId = correlationId
            )

            verify { customerRepository.save(any()) }
            assertEquals(50, customerSlot.captured.profileCompleteness)
        }

        @Test
        fun `should not update customer when completeness score unchanged`() {
            val customer = createCustomer(50)
            val completeness = createCompleteness(50)

            every { customerRepository.findById(customerId) } returns Optional.of(customer)
            every { profileCompletenessCalculator.calculate(customer) } returns completeness

            val correlationId = UUID.randomUUID()

            service.checkAndUpdateCompletion(
                customerId = customerId,
                previousScore = 50,
                correlationId = correlationId
            )

            verify(exactly = 0) { customerRepository.save(any()) }
        }

        @Test
        fun `should publish ProfileCompleted event when profile reaches 100 percent`() {
            val customer = createCustomer(90)
            val completeness = createCompleteness(100)
            val eventSlot = slot<ProfileCompleted>()

            every { customerRepository.findById(customerId) } returns Optional.of(customer)
            every { profileCompletenessCalculator.calculate(customer) } returns completeness
            every { customerRepository.save(any()) } returns customer
            every { eventStoreRepository.append(capture(eventSlot)) } just runs
            every { outboxWriter.write(any<ProfileCompleted>(), ProfileCompleted.TOPIC) } just runs

            val correlationId = UUID.randomUUID()

            service.checkAndUpdateCompletion(
                customerId = customerId,
                previousScore = 90,
                correlationId = correlationId
            )

            verify { eventStoreRepository.append(any<ProfileCompleted>()) }
            verify { outboxWriter.write(any<ProfileCompleted>(), ProfileCompleted.TOPIC) }

            assertEquals(customerId, eventSlot.captured.payload.customerId)
            assertEquals(correlationId, eventSlot.captured.correlationId)
            assertNotNull(eventSlot.captured.payload.completedAt)
            assertNotNull(eventSlot.captured.payload.timeToComplete)
        }

        @Test
        fun `should not publish event when previous score was already 100`() {
            val customer = createCustomer(100)
            val completeness = createCompleteness(100)

            every { customerRepository.findById(customerId) } returns Optional.of(customer)
            every { profileCompletenessCalculator.calculate(customer) } returns completeness

            val correlationId = UUID.randomUUID()

            service.checkAndUpdateCompletion(
                customerId = customerId,
                previousScore = 100,
                correlationId = correlationId
            )

            verify(exactly = 0) { eventStoreRepository.append(any()) }
            verify(exactly = 0) { outboxWriter.write(any<ProfileCompleted>(), any()) }
        }

        @Test
        fun `should not publish event when score increases but not to 100`() {
            val customer = createCustomer(50)
            val completeness = createCompleteness(75)

            every { customerRepository.findById(customerId) } returns Optional.of(customer)
            every { profileCompletenessCalculator.calculate(customer) } returns completeness
            every { customerRepository.save(any()) } returns customer

            val correlationId = UUID.randomUUID()

            service.checkAndUpdateCompletion(
                customerId = customerId,
                previousScore = 50,
                correlationId = correlationId
            )

            verify(exactly = 0) { eventStoreRepository.append(any()) }
            verify(exactly = 0) { outboxWriter.write(any<ProfileCompleted>(), any()) }
        }

        @Test
        fun `should pass causation ID to event when provided`() {
            val customer = createCustomer(90)
            val completeness = createCompleteness(100)
            val eventSlot = slot<ProfileCompleted>()
            val correlationId = UUID.randomUUID()
            val causationId = UUID.randomUUID()

            every { customerRepository.findById(customerId) } returns Optional.of(customer)
            every { profileCompletenessCalculator.calculate(customer) } returns completeness
            every { customerRepository.save(any()) } returns customer
            every { eventStoreRepository.append(capture(eventSlot)) } just runs
            every { outboxWriter.write(any<ProfileCompleted>(), ProfileCompleted.TOPIC) } just runs

            service.checkAndUpdateCompletion(
                customerId = customerId,
                previousScore = 90,
                correlationId = correlationId,
                causationId = causationId
            )

            assertEquals(causationId, eventSlot.captured.causationId)
        }

        @Test
        fun `should record metrics when calculating completeness`() {
            val customer = createCustomer(50)
            val completeness = createCompleteness(75)

            every { customerRepository.findById(customerId) } returns Optional.of(customer)
            every { profileCompletenessCalculator.calculate(customer) } returns completeness
            every { customerRepository.save(any()) } returns customer

            val correlationId = UUID.randomUUID()

            service.checkAndUpdateCompletion(
                customerId = customerId,
                previousScore = 50,
                correlationId = correlationId
            )

            // Verify that the summary recorded the completeness score
            val summary = meterRegistry.get("profile.completeness.distribution").summary()
            assertTrue(summary.count() > 0)
        }

        @Test
        fun `should increment counter when profile completes`() {
            val customer = createCustomer(90)
            val completeness = createCompleteness(100)

            every { customerRepository.findById(customerId) } returns Optional.of(customer)
            every { profileCompletenessCalculator.calculate(customer) } returns completeness
            every { customerRepository.save(any()) } returns customer
            every { eventStoreRepository.append(any<ProfileCompleted>()) } just runs
            every { outboxWriter.write(any<ProfileCompleted>(), ProfileCompleted.TOPIC) } just runs

            val correlationId = UUID.randomUUID()

            service.checkAndUpdateCompletion(
                customerId = customerId,
                previousScore = 90,
                correlationId = correlationId
            )

            val counter = meterRegistry.get("profile.completed").counter()
            assertEquals(1.0, counter.count())
        }
    }

    @Nested
    inner class GetCompleteness {

        @Test
        fun `should return null when customer not found`() {
            every { customerRepository.findById(customerId) } returns Optional.empty()

            val result = service.getCompleteness(customerId)

            assertNull(result)
        }

        @Test
        fun `should return completeness for found customer`() {
            val customer = createCustomer()
            val completeness = createCompleteness(50)
            every { customerRepository.findById(customerId) } returns Optional.of(customer)
            every { profileCompletenessCalculator.calculate(customer) } returns completeness

            val result = service.getCompleteness(customerId)

            assertNotNull(result)
            assertEquals(50, result.overallScore)
        }

        @Test
        fun `should not update customer when getting completeness`() {
            val customer = createCustomer()
            val completeness = createCompleteness(50)
            every { customerRepository.findById(customerId) } returns Optional.of(customer)
            every { profileCompletenessCalculator.calculate(customer) } returns completeness

            service.getCompleteness(customerId)

            verify(exactly = 0) { customerRepository.save(any()) }
        }

        @Test
        fun `should not publish events when getting completeness`() {
            val customer = createCustomer()
            val completeness = createCompleteness(100)
            every { customerRepository.findById(customerId) } returns Optional.of(customer)
            every { profileCompletenessCalculator.calculate(customer) } returns completeness

            service.getCompleteness(customerId)

            verify(exactly = 0) { eventStoreRepository.append(any()) }
            verify(exactly = 0) { outboxWriter.write(any<ProfileCompleted>(), any()) }
        }
    }
}
