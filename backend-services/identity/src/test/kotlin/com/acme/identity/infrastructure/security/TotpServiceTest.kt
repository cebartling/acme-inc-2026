package com.acme.identity.infrastructure.security

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TotpServiceTest {

    private lateinit var totpService: TotpService

    @BeforeEach
    fun setUp() {
        totpService = TotpService()
    }

    @Test
    fun `generateSecret should return base32 encoded string of correct length`() {
        // When
        val secret = totpService.generateSecret()

        // Then
        assertTrue(secret.isNotEmpty())
        assertEquals(TotpService.SECRET_LENGTH, secret.length)
        assertTrue(secret.all { it.isUpperCase() || it.isDigit() })
    }

    @Test
    fun `generateSecret should return unique secrets`() {
        // When
        val secret1 = totpService.generateSecret()
        val secret2 = totpService.generateSecret()

        // Then
        assertNotEquals(secret1, secret2)
    }

    @Test
    fun `verifyCode should return true for valid code`() {
        // Given
        val secret = totpService.generateSecret()
        val code = totpService.generateCode(secret)

        // When
        val result = totpService.verifyCode(secret, code)

        // Then
        assertTrue(result)
    }

    @Test
    fun `verifyCode should return false for invalid code`() {
        // Given
        val secret = totpService.generateSecret()
        val invalidCode = "000000"

        // When
        val result = totpService.verifyCode(secret, invalidCode)

        // Then
        assertFalse(result)
    }

    @Test
    fun `verifyCode should return false for code with wrong length`() {
        // Given
        val secret = totpService.generateSecret()

        // When/Then
        assertFalse(totpService.verifyCode(secret, "12345")) // 5 digits
        assertFalse(totpService.verifyCode(secret, "1234567")) // 7 digits
        assertFalse(totpService.verifyCode(secret, "")) // empty
    }

    @Test
    fun `verifyCode should return false for code with non-numeric characters`() {
        // Given
        val secret = totpService.generateSecret()

        // When/Then
        assertFalse(totpService.verifyCode(secret, "12345a"))
        assertFalse(totpService.verifyCode(secret, "abcdef"))
        assertFalse(totpService.verifyCode(secret, "123 45"))
    }

    @Test
    fun `verifyCode should accept code from previous time step within tolerance`() {
        // Given
        val secret = totpService.generateSecret()
        val currentTimeStep = totpService.getCurrentTimeStep()
        val previousCode = totpService.generateCode(secret, currentTimeStep - 1)

        // When
        val result = totpService.verifyCode(secret, previousCode)

        // Then
        assertTrue(result, "Code from previous time step should be valid within tolerance")
    }

    @Test
    fun `verifyCode should accept code from next time step within tolerance`() {
        // Given
        val secret = totpService.generateSecret()
        val currentTimeStep = totpService.getCurrentTimeStep()
        val nextCode = totpService.generateCode(secret, currentTimeStep + 1)

        // When
        val result = totpService.verifyCode(secret, nextCode)

        // Then
        assertTrue(result, "Code from next time step should be valid within tolerance")
    }

    @Test
    fun `verifyCode should reject code outside tolerance window`() {
        // Given
        val secret = totpService.generateSecret()
        val currentTimeStep = totpService.getCurrentTimeStep()
        val oldCode = totpService.generateCode(secret, currentTimeStep - 3) // Outside ±1 tolerance

        // When
        val result = totpService.verifyCode(secret, oldCode)

        // Then
        assertFalse(result, "Code from 3 time steps ago should be invalid")
    }

    @Test
    fun `getCurrentTimeStep should return positive value`() {
        // When
        val timeStep = totpService.getCurrentTimeStep()

        // Then
        assertTrue(timeStep > 0)
    }

    @Test
    fun `hashCode should return consistent hash for same code`() {
        // Given
        val code = "123456"

        // When
        val hash1 = totpService.hashCode(code)
        val hash2 = totpService.hashCode(code)

        // Then
        assertEquals(hash1, hash2)
    }

    @Test
    fun `hashCode should return different hashes for different codes`() {
        // Given
        val code1 = "123456"
        val code2 = "654321"

        // When
        val hash1 = totpService.hashCode(code1)
        val hash2 = totpService.hashCode(code2)

        // Then
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `hashCode should return 64 character hex string`() {
        // Given
        val code = "123456"

        // When
        val hash = totpService.hashCode(code)

        // Then
        assertEquals(64, hash.length) // SHA-256 produces 32 bytes = 64 hex chars
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `generateCode should produce 6 digit codes`() {
        // Given
        val secret = totpService.generateSecret()

        // When
        val code = totpService.generateCode(secret)

        // Then
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun `generateCode should produce different codes for different time steps`() {
        // Given
        val secret = totpService.generateSecret()
        val currentTimeStep = totpService.getCurrentTimeStep()

        // When
        val code1 = totpService.generateCode(secret, currentTimeStep)
        val code2 = totpService.generateCode(secret, currentTimeStep + 1)

        // Then
        // Note: There's a small chance these could be the same, but highly unlikely
        assertNotEquals(code1, code2, "Codes for different time steps should usually differ")
    }
}
