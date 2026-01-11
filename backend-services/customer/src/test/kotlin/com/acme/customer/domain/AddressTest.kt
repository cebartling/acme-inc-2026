package com.acme.customer.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddressTest {

    @Test
    fun `create should create address with correct values`() {
        // Given
        val id = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val type = AddressType.SHIPPING
        val streetLine1 = "123 Main St"
        val streetLine2 = "Apt 4B"
        val city = "New York"
        val state = "NY"
        val postalCode = "10001"
        val country = "US"
        val label = "Home"
        val isDefault = true

        // When
        val address = Address.create(
            id = id,
            customerId = customerId,
            type = type,
            streetLine1 = streetLine1,
            streetLine2 = streetLine2,
            city = city,
            state = state,
            postalCode = postalCode,
            country = country,
            label = label,
            isDefault = isDefault
        )

        // Then
        assertEquals(id, address.id)
        assertEquals(customerId, address.customerId)
        assertEquals(type, address.type)
        assertEquals(streetLine1, address.streetLine1)
        assertEquals(streetLine2, address.streetLine2)
        assertEquals(city, address.city)
        assertEquals(state, address.state)
        assertEquals(postalCode, address.postalCode)
        assertEquals(country, address.country)
        assertEquals(label, address.label)
        assertTrue(address.isDefault)
        assertFalse(address.isValidated)
        assertNull(address.latitude)
        assertNull(address.longitude)
        assertNotNull(address.createdAt)
        assertNotNull(address.updatedAt)
    }

    @Test
    fun `create should allow null optional fields`() {
        // Given
        val id = UUID.randomUUID()
        val customerId = UUID.randomUUID()

        // When
        val address = Address.create(
            id = id,
            customerId = customerId,
            type = AddressType.BILLING,
            streetLine1 = "456 Oak Ave",
            streetLine2 = null,
            city = "Boston",
            state = "MA",
            postalCode = "02101",
            country = "US",
            label = null,
            isDefault = false
        )

        // Then
        assertNull(address.streetLine2)
        assertNull(address.label)
        assertFalse(address.isDefault)
    }

    @Test
    fun `isPOBox should return true for PO Box addresses`() {
        // Test various PO Box patterns
        val poBoxPatterns = listOf(
            "PO Box 123",
            "P.O. Box 456",
            "P O Box 789",
            "Post Office Box 101",
            "po box 202"
        )

        poBoxPatterns.forEach { streetLine ->
            val address = createTestAddress(streetLine1 = streetLine)
            assertTrue(address.isPOBox(), "Expected '$streetLine' to be detected as PO Box")
        }
    }

    @Test
    fun `isPOBox should return false for regular addresses`() {
        val regularAddresses = listOf(
            "123 Main Street",
            "456 Oak Avenue",
            "789 Broadway",
            "100 Post Road", // Contains "Post" but not a PO Box
            "200 Boxing Lane" // Contains "Box" but not a PO Box
        )

        regularAddresses.forEach { streetLine ->
            val address = createTestAddress(streetLine1 = streetLine)
            assertFalse(address.isPOBox(), "Expected '$streetLine' to NOT be detected as PO Box")
        }
    }

    @Test
    fun `updateDetails should update all address fields`() {
        // Given
        val address = createTestAddress()
        val originalUpdatedAt = address.updatedAt

        // Wait a bit for timestamp difference
        Thread.sleep(10)

        // When
        address.updateDetails(
            streetLine1 = "789 New Street",
            streetLine2 = "Suite 100",
            city = "Los Angeles",
            state = "CA",
            postalCode = "90001",
            country = "US",
            label = "Work"
        )

        // Then
        assertEquals("789 New Street", address.streetLine1)
        assertEquals("Suite 100", address.streetLine2)
        assertEquals("Los Angeles", address.city)
        assertEquals("CA", address.state)
        assertEquals("90001", address.postalCode)
        assertEquals("US", address.country)
        assertEquals("Work", address.label)
        assertTrue(address.updatedAt.isAfter(originalUpdatedAt))
    }

    @Test
    fun `updateType should change address type`() {
        // Given
        val address = createTestAddress(type = AddressType.SHIPPING)

        // When
        address.updateType(AddressType.BILLING)

        // Then
        assertEquals(AddressType.BILLING, address.type)
    }

    @Test
    fun `setAsDefault should update isDefault flag`() {
        // Given
        val address = createTestAddress(isDefault = false)
        assertFalse(address.isDefault)

        // When
        address.setAsDefault(true)

        // Then
        assertTrue(address.isDefault)
    }

    @Test
    fun `setAsDefault should allow unsetting default`() {
        // Given
        val address = createTestAddress(isDefault = true)
        assertTrue(address.isDefault)

        // When
        address.setAsDefault(false)

        // Then
        assertFalse(address.isDefault)
    }

    @Test
    fun `setValidationResult should update validation fields`() {
        // Given
        val address = createTestAddress()
        val latitude = java.math.BigDecimal("40.7128")
        val longitude = java.math.BigDecimal("-74.0060")
        val details = """{"status": "valid", "accuracy": "high"}"""

        // When
        address.setValidationResult(
            isValid = true,
            latitude = latitude,
            longitude = longitude,
            details = details
        )

        // Then
        assertTrue(address.isValidated)
        assertEquals(latitude, address.latitude)
        assertEquals(longitude, address.longitude)
        assertEquals(details, address.validationDetails)
    }

    @Test
    fun `setValidationResult should handle invalid address`() {
        // Given
        val address = createTestAddress()

        // When
        address.setValidationResult(
            isValid = false,
            latitude = null,
            longitude = null,
            details = """{"status": "invalid", "reason": "address not found"}"""
        )

        // Then
        assertFalse(address.isValidated)
        assertNull(address.latitude)
        assertNull(address.longitude)
    }

    @Test
    fun `getFormattedAddress should format address correctly`() {
        // Given
        val address = Address.create(
            id = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            type = AddressType.SHIPPING,
            streetLine1 = "123 Main St",
            streetLine2 = "Apt 4B",
            city = "New York",
            state = "NY",
            postalCode = "10001",
            country = "US",
            label = null,
            isDefault = false
        )

        // When
        val formatted = address.getFormattedAddress()

        // Then
        assertEquals("123 Main St, Apt 4B, New York, NY 10001, US", formatted)
    }

    @Test
    fun `getFormattedAddress should handle missing streetLine2`() {
        // Given
        val address = Address.create(
            id = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            type = AddressType.SHIPPING,
            streetLine1 = "123 Main St",
            streetLine2 = null,
            city = "New York",
            state = "NY",
            postalCode = "10001",
            country = "US",
            label = null,
            isDefault = false
        )

        // When
        val formatted = address.getFormattedAddress()

        // Then
        assertEquals("123 Main St, New York, NY 10001, US", formatted)
    }

    @Test
    fun `equals should return true for same id`() {
        // Given
        val id = UUID.randomUUID()
        val address1 = createTestAddress(id = id)
        val address2 = createTestAddress(id = id)

        // When/Then
        assertEquals(address1, address2)
    }

    @Test
    fun `equals should return false for different id`() {
        // Given
        val address1 = createTestAddress(id = UUID.randomUUID())
        val address2 = createTestAddress(id = UUID.randomUUID())

        // When/Then
        assertNotEquals(address1, address2)
    }

    @Test
    fun `hashCode should be based on id`() {
        // Given
        val id = UUID.randomUUID()
        val address1 = createTestAddress(id = id)
        val address2 = createTestAddress(id = id)

        // When/Then
        assertEquals(address1.hashCode(), address2.hashCode())
    }

    @Test
    fun `hasCoordinates should return true when both lat and long are set`() {
        // Given
        val address = createTestAddress()
        address.setValidationResult(
            isValid = true,
            latitude = java.math.BigDecimal("40.7128"),
            longitude = java.math.BigDecimal("-74.0060"),
            details = null
        )

        // When/Then
        assertTrue(address.hasCoordinates())
    }

    @Test
    fun `hasCoordinates should return false when coordinates are null`() {
        // Given
        val address = createTestAddress()

        // When/Then
        assertFalse(address.hasCoordinates())
    }

    @Test
    fun `hasCoordinates should return false when only latitude is set`() {
        // Given
        val address = createTestAddress()
        address.latitude = java.math.BigDecimal("40.7128")

        // When/Then
        assertFalse(address.hasCoordinates())
    }

    @Test
    fun `MAX_ADDRESSES_PER_TYPE should be 10`() {
        assertEquals(10, Address.MAX_ADDRESSES_PER_TYPE)
    }

    private fun createTestAddress(
        id: UUID = UUID.randomUUID(),
        customerId: UUID = UUID.randomUUID(),
        type: AddressType = AddressType.SHIPPING,
        streetLine1: String = "123 Main St",
        streetLine2: String? = null,
        city: String = "New York",
        state: String = "NY",
        postalCode: String = "10001",
        country: String = "US",
        label: String? = null,
        isDefault: Boolean = false
    ): Address {
        return Address.create(
            id = id,
            customerId = customerId,
            type = type,
            streetLine1 = streetLine1,
            streetLine2 = streetLine2,
            city = city,
            state = state,
            postalCode = postalCode,
            country = country,
            label = label,
            isDefault = isDefault
        )
    }
}
