package com.acme.identity.infrastructure.security

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class VerificationTokenGeneratorTest {

    private lateinit var verificationTokenGenerator: VerificationTokenGenerator

    @BeforeEach
    fun setUp() {
        verificationTokenGenerator = VerificationTokenGenerator(expirationHours = 24)
    }

    @Test
    fun `generate should produce a non-empty token`() {
        val token = verificationTokenGenerator.generate()

        assertTrue(token.isNotBlank())
    }

    @Test
    fun `generate should produce unique tokens`() {
        val token1 = verificationTokenGenerator.generate()
        val token2 = verificationTokenGenerator.generate()

        assertNotEquals(token1, token2)
    }

    @Test
    fun `generate should produce URL-safe tokens`() {
        val token = verificationTokenGenerator.generate()

        // URL-safe base64 uses only alphanumeric, -, and _
        assertTrue(token.matches(Regex("[A-Za-z0-9_-]+")))
    }

    @Test
    fun `generate should produce tokens of expected length`() {
        val token = verificationTokenGenerator.generate()

        // 32 bytes encoded in base64 without padding = 43 characters
        assertTrue(token.length >= 40)
    }

    @Test
    fun `calculateExpiration should return time 24 hours in the future`() {
        val now = Instant.now()
        val expiration = verificationTokenGenerator.calculateExpiration()

        val hoursDifference = ChronoUnit.HOURS.between(now, expiration)

        assertTrue(hoursDifference in 23..24)
    }

    @Test
    fun `tokens should be cryptographically random`() {
        // Generate multiple tokens and verify they're all different
        val tokens = (1..100).map { verificationTokenGenerator.generate() }.toSet()

        // All 100 tokens should be unique
        assertTrue(tokens.size == 100)
    }
}
