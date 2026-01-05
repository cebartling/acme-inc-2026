package com.acme.customer.infrastructure.security

import org.junit.jupiter.api.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CustomerIdGeneratorTest {

    private val generator = CustomerIdGenerator()

    @Test
    fun `generate should return valid UUID`() {
        // When
        val id = generator.generate()

        // Then
        assertTrue(id.toString().matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }

    @Test
    fun `generate should return unique IDs each time`() {
        // When
        val id1 = generator.generate()
        val id2 = generator.generate()
        val id3 = generator.generate()

        // Then
        assertNotEquals(id1, id2)
        assertNotEquals(id2, id3)
        assertNotEquals(id1, id3)
    }

    @Test
    fun `generate should return time-ordered UUIDs`() {
        // When
        val ids = (1..100).map { generator.generate() }

        // Then - UUIDs should be in ascending order when compared as strings
        // (UUID v7 is time-ordered, so later UUIDs should be greater)
        for (i in 0 until ids.size - 1) {
            assertTrue(
                ids[i].toString() < ids[i + 1].toString(),
                "Expected ${ids[i]} to be less than ${ids[i + 1]}"
            )
        }
    }

    @Test
    fun `generateCustomerId should return CustomerId wrapper`() {
        // When
        val customerId = generator.generateCustomerId()

        // Then
        assertTrue(customerId.value.toString().matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }
}
