package com.acme.customer.application

import com.acme.customer.api.v1.dto.AddAddressRequest
import com.acme.customer.api.v1.dto.StreetDto
import com.acme.customer.domain.Address
import com.acme.customer.domain.AddressType
import com.acme.customer.domain.Customer
import com.acme.customer.infrastructure.messaging.OutboxWriter
import com.acme.customer.infrastructure.persistence.AddressRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import com.acme.customer.infrastructure.persistence.EventStoreRepository
import com.acme.customer.infrastructure.security.CustomerIdGenerator
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AddAddressUseCaseTest {

    private lateinit var customerRepository: CustomerRepository
    private lateinit var addressRepository: AddressRepository
    private lateinit var eventStoreRepository: EventStoreRepository
    private lateinit var outboxWriter: OutboxWriter
    private lateinit var customerIdGenerator: CustomerIdGenerator
    private lateinit var profileCompletionService: ProfileCompletionService
    private lateinit var useCase: AddAddressUseCase

    @BeforeEach
    fun setUp() {
        customerRepository = mockk()
        addressRepository = mockk()
        eventStoreRepository = mockk()
        outboxWriter = mockk()
        customerIdGenerator = mockk()
        profileCompletionService = mockk()

        every { profileCompletionService.checkAndUpdateCompletion(any(), any(), any(), any()) } returns null

        useCase = AddAddressUseCase(
            customerRepository = customerRepository,
            addressRepository = addressRepository,
            eventStoreRepository = eventStoreRepository,
            outboxWriter = outboxWriter,
            customerIdGenerator = customerIdGenerator,
            profileCompletionService = profileCompletionService,
            meterRegistry = SimpleMeterRegistry()
        )
    }

    @Test
    fun `execute should add address successfully`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createMockCustomer(customerId, userId)
        val request = createValidRequest()

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { addressRepository.countByCustomerIdAndType(customerId, AddressType.SHIPPING) } returns 0
        every { addressRepository.existsByCustomerIdAndLabel(customerId, any()) } returns false
        every { customerIdGenerator.generate() } returns addressId
        every { addressRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { outboxWriter.write(any(), any()) } just Runs

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is AddAddressResult.Success)
        val success = result as AddAddressResult.Success
        assertEquals(addressId, success.address.id)
        assertEquals(customerId, success.address.customerId)
        assertEquals(AddressType.SHIPPING, success.address.type)
        assertEquals("123 Main St", success.address.streetLine1)
        assertEquals("New York", success.address.city)

        verify { addressRepository.save(any()) }
        verify { eventStoreRepository.append(any()) }
        verify { outboxWriter.write(any(), any()) }
    }

    @Test
    fun `execute should return CustomerNotFound when customer does not exist`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val request = createValidRequest()

        every { customerRepository.findById(customerId) } returns Optional.empty()

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is AddAddressResult.CustomerNotFound)
        val notFound = result as AddAddressResult.CustomerNotFound
        assertEquals(customerId, notFound.customerId)

        verify(exactly = 0) { addressRepository.save(any()) }
    }

    @Test
    fun `execute should return Unauthorized when user does not own customer`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val differentUserId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createMockCustomer(customerId, differentUserId)
        val request = createValidRequest()

        every { customerRepository.findById(customerId) } returns Optional.of(customer)

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is AddAddressResult.Unauthorized)
        val unauthorized = result as AddAddressResult.Unauthorized
        assertEquals(customerId, unauthorized.customerId)
        assertEquals(userId, unauthorized.userId)

        verify(exactly = 0) { addressRepository.save(any()) }
    }

    @Test
    fun `execute should return ValidationFailed for blank street`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createMockCustomer(customerId, userId)
        val request = AddAddressRequest(
            type = "SHIPPING",
            street = StreetDto(line1 = "   "),
            city = "New York",
            state = "NY",
            postalCode = "10001",
            country = "US"
        )

        every { customerRepository.findById(customerId) } returns Optional.of(customer)

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is AddAddressResult.ValidationFailed)
        val failed = result as AddAddressResult.ValidationFailed
        assertTrue(failed.errors.containsKey("street.line1"))

        verify(exactly = 0) { addressRepository.save(any()) }
    }

    @Test
    fun `execute should return ValidationFailed for invalid country code`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createMockCustomer(customerId, userId)
        val request = AddAddressRequest(
            type = "SHIPPING",
            street = StreetDto(line1 = "123 Main St"),
            city = "New York",
            state = "NY",
            postalCode = "10001",
            country = "USA" // Should be 2 letters
        )

        every { customerRepository.findById(customerId) } returns Optional.of(customer)

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is AddAddressResult.ValidationFailed)
        val failed = result as AddAddressResult.ValidationFailed
        assertTrue(failed.errors.containsKey("country"))

        verify(exactly = 0) { addressRepository.save(any()) }
    }

    @Test
    fun `execute should return ValidationFailed for invalid address type`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createMockCustomer(customerId, userId)
        val request = AddAddressRequest(
            type = "INVALID_TYPE",
            street = StreetDto(line1 = "123 Main St"),
            city = "New York",
            state = "NY",
            postalCode = "10001",
            country = "US"
        )

        every { customerRepository.findById(customerId) } returns Optional.of(customer)

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is AddAddressResult.ValidationFailed)
        val failed = result as AddAddressResult.ValidationFailed
        assertTrue(failed.errors.containsKey("type"))

        verify(exactly = 0) { addressRepository.save(any()) }
    }

    @Test
    fun `execute should return MaxAddressesReached when limit exceeded`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createMockCustomer(customerId, userId)
        val request = createValidRequest()

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { addressRepository.countByCustomerIdAndType(customerId, AddressType.SHIPPING) } returns Address.MAX_ADDRESSES_PER_TYPE

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is AddAddressResult.MaxAddressesReached)
        val maxReached = result as AddAddressResult.MaxAddressesReached
        assertEquals("SHIPPING", maxReached.type)
        assertEquals(Address.MAX_ADDRESSES_PER_TYPE, maxReached.maxAllowed)

        verify(exactly = 0) { addressRepository.save(any()) }
    }

    @Test
    fun `execute should return DuplicateLabel when label already exists`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createMockCustomer(customerId, userId)
        val request = AddAddressRequest(
            type = "SHIPPING",
            label = "Home",
            street = StreetDto(line1 = "123 Main St"),
            city = "New York",
            state = "NY",
            postalCode = "10001",
            country = "US"
        )

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { addressRepository.countByCustomerIdAndType(customerId, AddressType.SHIPPING) } returns 0
        every { addressRepository.existsByCustomerIdAndLabel(customerId, "Home") } returns true

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is AddAddressResult.DuplicateLabel)
        val duplicate = result as AddAddressResult.DuplicateLabel
        assertEquals("Home", duplicate.label)

        verify(exactly = 0) { addressRepository.save(any()) }
    }

    @Test
    fun `execute should return POBoxNotAllowedForShipping for PO Box shipping address`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createMockCustomer(customerId, userId)
        val request = AddAddressRequest(
            type = "SHIPPING",
            street = StreetDto(line1 = "PO Box 123"),
            city = "New York",
            state = "NY",
            postalCode = "10001",
            country = "US"
        )

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { addressRepository.countByCustomerIdAndType(customerId, AddressType.SHIPPING) } returns 0
        every { addressRepository.existsByCustomerIdAndLabel(customerId, any()) } returns false
        every { customerIdGenerator.generate() } returns addressId

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is AddAddressResult.POBoxNotAllowedForShipping)

        verify(exactly = 0) { addressRepository.save(any()) }
    }

    @Test
    fun `execute should allow PO Box for billing address`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createMockCustomer(customerId, userId)
        val request = AddAddressRequest(
            type = "BILLING",
            street = StreetDto(line1 = "PO Box 123"),
            city = "New York",
            state = "NY",
            postalCode = "10001",
            country = "US"
        )

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { addressRepository.countByCustomerIdAndType(customerId, AddressType.BILLING) } returns 0
        every { addressRepository.existsByCustomerIdAndLabel(customerId, any()) } returns false
        every { customerIdGenerator.generate() } returns addressId
        every { addressRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { outboxWriter.write(any(), any()) } just Runs

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is AddAddressResult.Success)
        verify { addressRepository.save(any()) }
    }

    @Test
    fun `execute should clear existing default when adding new default address`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createMockCustomer(customerId, userId)
        val request = AddAddressRequest(
            type = "SHIPPING",
            street = StreetDto(line1 = "123 Main St"),
            city = "New York",
            state = "NY",
            postalCode = "10001",
            country = "US",
            isDefault = true
        )

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { addressRepository.countByCustomerIdAndType(customerId, AddressType.SHIPPING) } returns 1
        every { addressRepository.existsByCustomerIdAndLabel(customerId, any()) } returns false
        every { customerIdGenerator.generate() } returns addressId
        every { addressRepository.clearDefaultForType(customerId, AddressType.SHIPPING) } returns 1
        every { addressRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { outboxWriter.write(any(), any()) } just Runs

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is AddAddressResult.Success)
        val success = result as AddAddressResult.Success
        assertTrue(success.address.isDefault)

        verify { addressRepository.clearDefaultForType(customerId, AddressType.SHIPPING) }
    }

    @Test
    fun `execute should return Failure when exception occurs`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createMockCustomer(customerId, userId)
        val request = createValidRequest()

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { addressRepository.countByCustomerIdAndType(customerId, AddressType.SHIPPING) } returns 0
        every { addressRepository.existsByCustomerIdAndLabel(customerId, any()) } returns false
        every { customerIdGenerator.generate() } throws RuntimeException("Database error")

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is AddAddressResult.Failure)
        val failure = result as AddAddressResult.Failure
        assertTrue(failure.message.contains("Database error"))
    }

    private fun createMockCustomer(customerId: UUID, userId: UUID): Customer {
        return mockk {
            every { id } returns customerId
            every { this@mockk.userId } returns userId
            every { profileCompleteness } returns 25
        }
    }

    private fun createValidRequest(): AddAddressRequest {
        return AddAddressRequest(
            type = "SHIPPING",
            street = StreetDto(line1 = "123 Main St", line2 = "Apt 4B"),
            city = "New York",
            state = "NY",
            postalCode = "10001",
            country = "US",
            isDefault = false
        )
    }
}
