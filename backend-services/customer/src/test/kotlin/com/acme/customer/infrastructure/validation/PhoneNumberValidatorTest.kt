package com.acme.customer.infrastructure.validation

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhoneNumberValidatorTest {

    private lateinit var validator: PhoneNumberValidator

    @BeforeEach
    fun setUp() {
        validator = PhoneNumberValidator()
    }

    @Test
    fun `validate should return Valid for valid US phone number`() {
        // Given
        val countryCode = "+1"
        val number = "2025551234"  // DC area code with valid format

        // When
        val result = validator.validate(countryCode, number)

        // Then
        assertTrue(result is PhoneValidationResult.Valid)
        val valid = result as PhoneValidationResult.Valid
        assertEquals("+1", valid.countryCode)
        assertEquals("2025551234", valid.nationalNumber)
        assertEquals("+12025551234", valid.formattedNumber)
    }

    @Test
    fun `validate should return Valid for US phone number without plus prefix`() {
        // Given
        val countryCode = "1"
        val number = "2025551234"  // DC area code with valid format

        // When
        val result = validator.validate(countryCode, number)

        // Then
        assertTrue(result is PhoneValidationResult.Valid)
    }

    @Test
    fun `validate should return Valid for UK phone number`() {
        // Given
        val countryCode = "+44"
        val number = "7911123456"

        // When
        val result = validator.validate(countryCode, number)

        // Then
        assertTrue(result is PhoneValidationResult.Valid)
        val valid = result as PhoneValidationResult.Valid
        assertEquals("+44", valid.countryCode)
    }

    @Test
    fun `validate should return Valid for German phone number`() {
        // Given
        val countryCode = "+49"
        val number = "15123456789"

        // When
        val result = validator.validate(countryCode, number)

        // Then
        assertTrue(result is PhoneValidationResult.Valid)
        val valid = result as PhoneValidationResult.Valid
        assertEquals("+49", valid.countryCode)
    }

    @Test
    fun `validate should return Invalid for too short phone number`() {
        // Given
        val countryCode = "+1"
        val number = "123"

        // When
        val result = validator.validate(countryCode, number)

        // Then
        assertTrue(result is PhoneValidationResult.Invalid)
    }

    @Test
    fun `validate should return Invalid for too long phone number`() {
        // Given
        val countryCode = "+1"
        val number = "12345678901234567890"

        // When
        val result = validator.validate(countryCode, number)

        // Then
        assertTrue(result is PhoneValidationResult.Invalid)
    }

    @Test
    fun `validate should return Invalid for invalid country code`() {
        // Given
        val countryCode = "+999"
        val number = "5551234567"

        // When
        val result = validator.validate(countryCode, number)

        // Then
        assertTrue(result is PhoneValidationResult.Invalid)
        val invalid = result as PhoneValidationResult.Invalid
        assertTrue(invalid.message.contains("country code", ignoreCase = true))
    }

    @Test
    fun `validate should return Invalid for non-numeric input`() {
        // Given
        val countryCode = "+1"
        val number = "not-a-number"

        // When
        val result = validator.validate(countryCode, number)

        // Then
        assertTrue(result is PhoneValidationResult.Invalid)
    }

    @Test
    fun `validate should handle phone number with spaces`() {
        // Given - some parsing libraries can handle formatted numbers
        val countryCode = "+1"
        val number = "202 555 1234"  // DC area code with valid format

        // When
        val result = validator.validate(countryCode, number)

        // Then
        assertTrue(result is PhoneValidationResult.Valid)
    }

    @Test
    fun `validate should handle phone number with dashes`() {
        // Given
        val countryCode = "+1"
        val number = "202-555-1234"  // DC area code with valid format

        // When
        val result = validator.validate(countryCode, number)

        // Then
        assertTrue(result is PhoneValidationResult.Valid)
    }

    @Test
    fun `validateWithRegion should return Valid for number with default region`() {
        // Given
        val number = "2025551234"  // DC area code with valid format
        val defaultRegion = "US"

        // When
        val result = validator.validateWithRegion(number, defaultRegion)

        // Then
        assertTrue(result is PhoneValidationResult.Valid)
        val valid = result as PhoneValidationResult.Valid
        assertEquals("+1", valid.countryCode)
    }

    @Test
    fun `validateWithRegion should return Valid for international number`() {
        // Given
        val number = "+447911123456"
        val defaultRegion = "US"

        // When
        val result = validator.validateWithRegion(number, defaultRegion)

        // Then
        assertTrue(result is PhoneValidationResult.Valid)
        val valid = result as PhoneValidationResult.Valid
        assertEquals("+44", valid.countryCode)
    }

    @Test
    fun `validateWithRegion should return Invalid for invalid number`() {
        // Given
        val number = "123"
        val defaultRegion = "US"

        // When
        val result = validator.validateWithRegion(number, defaultRegion)

        // Then
        assertTrue(result is PhoneValidationResult.Invalid)
    }
}
