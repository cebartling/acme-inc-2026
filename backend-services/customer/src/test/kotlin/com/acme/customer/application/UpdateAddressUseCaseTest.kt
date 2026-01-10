package com.acme.customer.application

import com.acme.customer.api.v1.dto.StreetDto
import com.acme.customer.api.v1.dto.UpdateAddressRequest
import com.acme.customer.domain.Address
import com.acme.customer.domain.AddressType
import com.acme.customer.domain.Customer
import com.acme.customer.infrastructure.messaging.OutboxWriter
import com.acme.customer.infrastructure.persistence.AddressRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import com.acme.customer.infrastructure.persistence.EventStoreRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateAddressUseCaseTest {

    private lateinit var customerRepository: CustomerRepository
    private lateinit var addressRepository: AddressRepository
    private lateinit var eventStoreRepository: EventStoreRepository
    private lateinit var outboxWriter: OutboxWriter
    private lateinit var useCase: UpdateAddressUseCase

    @BeforeEach
    fun setUp() {
        customerRepository = mockk()
        addressRepository = mockk()
        eventStoreRepository = mockk()
        outboxWriter = mockk()

        useCase = UpdateAddressUseCase(
            customerRepository = customerRepository,
            addressRepository = addressRepository,
            eventStoreRepository = eventStoreRepository,
            outboxWriter = outboxWriter,
            meterRegistry = SimpleMeterRegistry()
        )
    }

    @Test
    fun `execute should update address successfully`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createMockCustomer(customerId, userId)
        val address = createRealAddress(addressId, customerId)
        val request = UpdateAddressRequest(city = "Los Angeles")

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { addressRepository.findById(addressId) } returns Optional.of(address)
        every { addressRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { outboxWriter.write(any(), any()) } just Runs

        // When
        val result = useCase.execute(customerId, addressId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdateAddressResult.Success)
        val success = result as UpdateAddressResult.Success
        assertEquals("Los Angeles", success.address.city)
        assertTrue(success.changedFields.contains("city"))

        verify { addressRepository.save(any()) }
        verify { eventStoreRepository.append(any()) }
        verify { outboxWriter.write(any(), any()) }
    }

    @Test
    fun `execute should return NoUpdates when request has no changes`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val request = UpdateAddressRequest() // All fields null

        // When
        val result = useCase.execute(customerId, addressId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdateAddressResult.NoUpdates)

        verify(exactly = 0) { customerRepository.findById(any()) }
        verify(exactly = 0) { addressRepository.save(any()) }
    }

    @Test
    fun `execute should return CustomerNotFound when customer does not exist`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val request = UpdateAddressRequest(city = "New City")

        every { customerRepository.findById(customerId) } returns Optional.empty()

        // When
        val result = useCase.execute(customerId, addressId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdateAddressResult.CustomerNotFound)
        val notFound = result as UpdateAddressResult.CustomerNotFound
        assertEquals(customerId, notFound.customerId)
    }

    @Test
    fun `execute should return Unauthorized when user does not own customer`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val differentUserId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createMockCustomer(customerId, differentUserId)
        val request = UpdateAddressRequest(city = "New City")

        every { customerRepository.findById(customerId) } returns Optional.of(customer)

        // When
        val result = useCase.execute(customerId, addressId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdateAddressResult.Unauthorized)
        val unauthorized = result as UpdateAddressResult.Unauthorized
        assertEquals(customerId, unauthorized.customerId)
        assertEquals(userId, unauthorized.userId)
    }

    @Test
    fun `execute should return AddressNotFound when address does not exist`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createMockCustomer(customerId, userId)
        val request = UpdateAddressRequest(city = "New City")

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { addressRepository.findById(addressId) } returns Optional.empty()

        // When
        val result = useCase.execute(customerId, addressId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdateAddressResult.AddressNotFound)
        val notFound = result as UpdateAddressResult.AddressNotFound
        assertEquals(addressId, notFound.addressId)
    }

    @Test
    fun `execute should return AddressNotFound when address belongs to different customer`() {
        // Given
        val customerId = UUID.randomUUID()
        val differentCustomerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createMockCustomer(customerId, userId)
        val address = createRealAddress(addressId, differentCustomerId)
        val request = UpdateAddressRequest(city = "New City")

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { addressRepository.findById(addressId) } returns Optional.of(address)

        // When
        val result = useCase.execute(customerId, addressId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdateAddressResult.AddressNotFound)
    }

    @Test
    fun `execute should return ValidationFailed for invalid address type`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createMockCustomer(customerId, userId)
        val address = createRealAddress(addressId, customerId)
        val request = UpdateAddressRequest(type = "INVALID_TYPE")

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { addressRepository.findById(addressId) } returns Optional.of(address)

        // When
        val result = useCase.execute(customerId, addressId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdateAddressResult.ValidationFailed)
        val failed = result as UpdateAddressResult.ValidationFailed
        assertTrue(failed.errors.containsKey("type"))
    }

    @Test
    fun `execute should return ValidationFailed for blank city`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createMockCustomer(customerId, userId)
        val address = createRealAddress(addressId, customerId)
        val request = UpdateAddressRequest(city = "   ")

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { addressRepository.findById(addressId) } returns Optional.of(address)

        // When
        val result = useCase.execute(customerId, addressId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdateAddressResult.ValidationFailed)
        val failed = result as UpdateAddressResult.ValidationFailed
        assertTrue(failed.errors.containsKey("city"))
    }

    @Test
    fun `execute should return DuplicateLabel when label already exists`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createMockCustomer(customerId, userId)
        val address = createRealAddress(addressId, customerId)
        val request = UpdateAddressRequest(label = "Existing Label")

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { addressRepository.findById(addressId) } returns Optional.of(address)
        every { addressRepository.existsByCustomerIdAndLabelExcluding(customerId, "Existing Label", addressId) } returns true

        // When
        val result = useCase.execute(customerId, addressId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdateAddressResult.DuplicateLabel)
        val duplicate = result as UpdateAddressResult.DuplicateLabel
        assertEquals("Existing Label", duplicate.label)
    }

    @Test
    fun `execute should return POBoxNotAllowedForShipping when updating to PO Box for shipping`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createMockCustomer(customerId, userId)
        val address = createRealAddress(addressId, customerId, type = AddressType.SHIPPING)
        val request = UpdateAddressRequest(
            street = StreetDto(line1 = "PO Box 123")
        )

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { addressRepository.findById(addressId) } returns Optional.of(address)

        // When
        val result = useCase.execute(customerId, addressId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdateAddressResult.POBoxNotAllowedForShipping)
    }

    @Test
    fun `execute should update type from shipping to billing`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createMockCustomer(customerId, userId)
        val address = createRealAddress(addressId, customerId, type = AddressType.SHIPPING)
        val request = UpdateAddressRequest(type = "BILLING")

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { addressRepository.findById(addressId) } returns Optional.of(address)
        every { addressRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { outboxWriter.write(any(), any()) } just Runs

        // When
        val result = useCase.execute(customerId, addressId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdateAddressResult.Success)
        val success = result as UpdateAddressResult.Success
        assertEquals(AddressType.BILLING, success.address.type)
        assertTrue(success.changedFields.contains("type"))
    }

    @Test
    fun `execute should update isDefault and clear previous default`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createMockCustomer(customerId, userId)
        val address = createRealAddress(addressId, customerId, isDefault = false)
        val request = UpdateAddressRequest(isDefault = true)

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { addressRepository.findById(addressId) } returns Optional.of(address)
        every { addressRepository.clearDefaultForType(customerId, AddressType.SHIPPING) } returns 1
        every { addressRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { outboxWriter.write(any(), any()) } just Runs

        // When
        val result = useCase.execute(customerId, addressId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdateAddressResult.Success)
        val success = result as UpdateAddressResult.Success
        assertTrue(success.address.isDefault)
        assertTrue(success.changedFields.contains("isDefault"))

        verify { addressRepository.clearDefaultForType(customerId, AddressType.SHIPPING) }
    }

    @Test
    fun `execute should return Failure when exception occurs`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createMockCustomer(customerId, userId)
        val address = createRealAddress(addressId, customerId)
        val request = UpdateAddressRequest(city = "New City")

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { addressRepository.findById(addressId) } returns Optional.of(address)
        every { addressRepository.save(any()) } throws RuntimeException("Database error")

        // When
        val result = useCase.execute(customerId, addressId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdateAddressResult.Failure)
        val failure = result as UpdateAddressResult.Failure
        assertTrue(failure.message.contains("Database error"))
    }

    private fun createMockCustomer(customerId: UUID, userId: UUID): Customer {
        return mockk {
            every { id } returns customerId
            every { this@mockk.userId } returns userId
        }
    }

    private fun createRealAddress(
        addressId: UUID,
        customerId: UUID,
        type: AddressType = AddressType.SHIPPING,
        isDefault: Boolean = false
    ): Address {
        return Address.create(
            id = addressId,
            customerId = customerId,
            type = type,
            streetLine1 = "123 Main St",
            streetLine2 = null,
            city = "New York",
            state = "NY",
            postalCode = "10001",
            country = "US",
            label = null,
            isDefault = isDefault
        )
    }
}
