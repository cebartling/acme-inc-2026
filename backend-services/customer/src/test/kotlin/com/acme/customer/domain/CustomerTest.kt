package com.acme.customer.domain

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class CustomerTest {

    @Test
    fun `createFromRegistration should create customer with default values`() {
        // Given
        val id = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val customerNumber = "ACME-202601-000001"
        val email = "test@example.com"
        val firstName = "Jane"
        val lastName = "Doe"
        val registeredAt = Instant.now()

        // When
        val customer = Customer.createFromRegistration(
            id = id,
            userId = userId,
            customerNumber = customerNumber,
            email = email,
            firstName = firstName,
            lastName = lastName,
            registeredAt = registeredAt
        )

        // Then
        assertEquals(id, customer.id)
        assertEquals(userId, customer.userId)
        assertEquals(customerNumber, customer.customerNumber)
        assertEquals(email, customer.email)
        assertEquals(firstName, customer.firstName)
        assertEquals(lastName, customer.lastName)
        assertEquals("Jane Doe", customer.displayName)
        assertFalse(customer.emailVerified)
        assertEquals(CustomerStatus.PENDING_VERIFICATION, customer.status)
        assertEquals(CustomerType.INDIVIDUAL, customer.type)
        assertEquals("en-US", customer.preferredLocale)
        assertEquals("UTC", customer.timezone)
        assertEquals("USD", customer.preferredCurrency)
        assertEquals(25, customer.profileCompleteness)
        assertEquals(registeredAt, customer.registeredAt)
        assertEquals(registeredAt, customer.lastActivityAt)
    }

    @Test
    fun `getCustomerId should return CustomerId value class`() {
        // Given
        val id = UUID.randomUUID()
        val customer = createTestCustomer(id)

        // When
        val customerId = customer.getCustomerId()

        // Then
        assertEquals(id, customerId.value)
    }

    @Test
    fun `getCustomerNumber should return CustomerNumber value class`() {
        // Given
        val customer = createTestCustomer()

        // When
        val customerNumber = customer.getCustomerNumber()

        // Then
        assertEquals("ACME-202601-000001", customerNumber.value)
    }

    @Test
    fun `equals should return true for same id`() {
        // Given
        val id = UUID.randomUUID()
        val customer1 = createTestCustomer(id)
        val customer2 = createTestCustomer(id)

        // When/Then
        assertEquals(customer1, customer2)
    }

    @Test
    fun `equals should return false for different id`() {
        // Given
        val customer1 = createTestCustomer(UUID.randomUUID())
        val customer2 = createTestCustomer(UUID.randomUUID())

        // When/Then
        assertNotEquals(customer1, customer2)
    }

    @Test
    fun `hashCode should be based on id`() {
        // Given
        val id = UUID.randomUUID()
        val customer1 = createTestCustomer(id)
        val customer2 = createTestCustomer(id)

        // When/Then
        assertEquals(customer1.hashCode(), customer2.hashCode())
    }

    private fun createTestCustomer(id: UUID = UUID.randomUUID()): Customer {
        return Customer.createFromRegistration(
            id = id,
            userId = UUID.randomUUID(),
            customerNumber = "ACME-202601-000001",
            email = "test@example.com",
            firstName = "Jane",
            lastName = "Doe",
            registeredAt = Instant.now()
        )
    }
}
