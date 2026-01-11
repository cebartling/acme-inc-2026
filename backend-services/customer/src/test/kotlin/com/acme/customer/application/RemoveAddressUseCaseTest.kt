package com.acme.customer.application

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
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoveAddressUseCaseTest {

    private lateinit var customerRepository: CustomerRepository
    private lateinit var addressRepository: AddressRepository
    private lateinit var eventStoreRepository: EventStoreRepository
    private lateinit var outboxWriter: OutboxWriter
    private lateinit var useCase: RemoveAddressUseCase

    @BeforeEach
    fun setUp() {
        customerRepository = mockk()
        addressRepository = mockk()
        eventStoreRepository = mockk()
        outboxWriter = mockk()

        useCase = RemoveAddressUseCase(
            customerRepository = customerRepository,
            addressRepository = addressRepository,
            eventStoreRepository = eventStoreRepository,
            outboxWriter = outboxWriter,
            meterRegistry = SimpleMeterRegistry()
        )
    }

    @Test
    fun `execute should remove address successfully`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createMockCustomer(customerId, userId)
        val address = createMockAddress(addressId, customerId, isDefault = false)

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { addressRepository.findById(addressId) } returns Optional.of(address)
        every { addressRepository.delete(address) } just Runs
        every { eventStoreRepository.append(any()) } just Runs
        every { outboxWriter.write(any(), any()) } just Runs

        // When
        val result = useCase.execute(customerId, addressId, userId, correlationId)

        // Then
        assertTrue(result is RemoveAddressResult.Success)
        val success = result as RemoveAddressResult.Success
        assertEquals(addressId, success.addressId)
        assertFalse(success.wasDefault)

        verify { addressRepository.delete(address) }
        verify { eventStoreRepository.append(any()) }
        verify { outboxWriter.write(any(), any()) }
    }

    @Test
    fun `execute should return wasDefault true when removing default address`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createMockCustomer(customerId, userId)
        val address = createMockAddress(addressId, customerId, isDefault = true)

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { addressRepository.findById(addressId) } returns Optional.of(address)
        every { addressRepository.delete(address) } just Runs
        every { eventStoreRepository.append(any()) } just Runs
        every { outboxWriter.write(any(), any()) } just Runs

        // When
        val result = useCase.execute(customerId, addressId, userId, correlationId)

        // Then
        assertTrue(result is RemoveAddressResult.Success)
        val success = result as RemoveAddressResult.Success
        assertTrue(success.wasDefault)
    }

    @Test
    fun `execute should return CustomerNotFound when customer does not exist`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()

        every { customerRepository.findById(customerId) } returns Optional.empty()

        // When
        val result = useCase.execute(customerId, addressId, userId, correlationId)

        // Then
        assertTrue(result is RemoveAddressResult.CustomerNotFound)
        val notFound = result as RemoveAddressResult.CustomerNotFound
        assertEquals(customerId, notFound.customerId)

        verify(exactly = 0) { addressRepository.delete(any()) }
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

        every { customerRepository.findById(customerId) } returns Optional.of(customer)

        // When
        val result = useCase.execute(customerId, addressId, userId, correlationId)

        // Then
        assertTrue(result is RemoveAddressResult.Unauthorized)
        val unauthorized = result as RemoveAddressResult.Unauthorized
        assertEquals(customerId, unauthorized.customerId)
        assertEquals(userId, unauthorized.userId)

        verify(exactly = 0) { addressRepository.delete(any()) }
    }

    @Test
    fun `execute should return AddressNotFound when address does not exist`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createMockCustomer(customerId, userId)

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { addressRepository.findById(addressId) } returns Optional.empty()

        // When
        val result = useCase.execute(customerId, addressId, userId, correlationId)

        // Then
        assertTrue(result is RemoveAddressResult.AddressNotFound)
        val notFound = result as RemoveAddressResult.AddressNotFound
        assertEquals(addressId, notFound.addressId)

        verify(exactly = 0) { addressRepository.delete(any()) }
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
        val address = createMockAddress(addressId, differentCustomerId, isDefault = false)

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { addressRepository.findById(addressId) } returns Optional.of(address)

        // When
        val result = useCase.execute(customerId, addressId, userId, correlationId)

        // Then
        assertTrue(result is RemoveAddressResult.AddressNotFound)

        verify(exactly = 0) { addressRepository.delete(any()) }
    }

    @Test
    fun `execute should return Failure when exception occurs`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createMockCustomer(customerId, userId)
        val address = createMockAddress(addressId, customerId, isDefault = false)

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { addressRepository.findById(addressId) } returns Optional.of(address)
        every { addressRepository.delete(address) } throws RuntimeException("Database error")

        // When
        val result = useCase.execute(customerId, addressId, userId, correlationId)

        // Then
        assertTrue(result is RemoveAddressResult.Failure)
        val failure = result as RemoveAddressResult.Failure
        assertTrue(failure.message.contains("Database error"))
    }

    private fun createMockCustomer(customerId: UUID, userId: UUID): Customer {
        return mockk {
            every { id } returns customerId
            every { this@mockk.userId } returns userId
        }
    }

    private fun createMockAddress(addressId: UUID, customerId: UUID, isDefault: Boolean): Address {
        return mockk {
            every { id } returns addressId
            every { this@mockk.customerId } returns customerId
            every { this@mockk.isDefault } returns isDefault
            every { type } returns AddressType.SHIPPING
        }
    }
}
