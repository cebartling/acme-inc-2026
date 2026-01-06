package com.acme.customer.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.YearMonth
import kotlin.test.assertEquals

class CustomerNumberTest {

    @Test
    fun `create should generate valid customer number format`() {
        // Given
        val yearMonth = YearMonth.of(2026, 1)
        val sequence = 142

        // When
        val customerNumber = CustomerNumber.create(yearMonth, sequence)

        // Then
        assertEquals("ACME-202601-000142", customerNumber.value)
    }

    @Test
    fun `create should pad sequence with leading zeros`() {
        // Given
        val yearMonth = YearMonth.of(2026, 3)
        val sequence = 1

        // When
        val customerNumber = CustomerNumber.create(yearMonth, sequence)

        // Then
        assertEquals("ACME-202603-000001", customerNumber.value)
    }

    @Test
    fun `create should handle maximum sequence number`() {
        // Given
        val yearMonth = YearMonth.of(2026, 12)
        val sequence = 999999

        // When
        val customerNumber = CustomerNumber.create(yearMonth, sequence)

        // Then
        assertEquals("ACME-202612-999999", customerNumber.value)
    }

    @Test
    fun `create should reject sequence number less than 1`() {
        // Given
        val yearMonth = YearMonth.of(2026, 1)

        // When/Then
        assertThrows<IllegalArgumentException> {
            CustomerNumber.create(yearMonth, 0)
        }
    }

    @Test
    fun `create should reject sequence number greater than 999999`() {
        // Given
        val yearMonth = YearMonth.of(2026, 1)

        // When/Then
        assertThrows<IllegalArgumentException> {
            CustomerNumber.create(yearMonth, 1000000)
        }
    }

    @Test
    fun `parse should accept valid customer number`() {
        // Given
        val value = "ACME-202601-000142"

        // When
        val customerNumber = CustomerNumber.parse(value)

        // Then
        assertEquals(value, customerNumber.value)
    }

    @Test
    fun `parse should reject invalid format`() {
        // When/Then
        assertThrows<IllegalArgumentException> {
            CustomerNumber.parse("INVALID-123")
        }
    }

    @Test
    fun `parse should reject wrong prefix`() {
        // When/Then
        assertThrows<IllegalArgumentException> {
            CustomerNumber.parse("ACMA-202601-000142")
        }
    }

    @Test
    fun `yearMonthKey should return YYYYMM format`() {
        // Given
        val yearMonth = YearMonth.of(2026, 1)

        // When
        val key = CustomerNumber.yearMonthKey(yearMonth)

        // Then
        assertEquals("202601", key)
    }

    @Test
    fun `toString should return the customer number value`() {
        // Given
        val customerNumber = CustomerNumber.create(YearMonth.of(2026, 1), 142)

        // When
        val result = customerNumber.toString()

        // Then
        assertEquals("ACME-202601-000142", result)
    }
}
