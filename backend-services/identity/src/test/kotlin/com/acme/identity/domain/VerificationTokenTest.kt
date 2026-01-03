package com.acme.identity.domain

import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class VerificationTokenTest {

    @Test
    fun `isExpired should return false for future expiration`() {
        val token = createToken(expiresAt = Instant.now().plus(1, ChronoUnit.HOURS))

        assertFalse(token.isExpired())
    }

    @Test
    fun `isExpired should return true for past expiration`() {
        val token = createToken(expiresAt = Instant.now().minus(1, ChronoUnit.HOURS))

        assertTrue(token.isExpired())
    }

    @Test
    fun `isUsed should return false when usedAt is null`() {
        val token = createToken()

        assertFalse(token.isUsed())
    }

    @Test
    fun `isUsed should return true when usedAt is set`() {
        val token = createToken().apply { usedAt = Instant.now() }

        assertTrue(token.isUsed())
    }

    @Test
    fun `isValid should return true for unused and not expired token`() {
        val token = createToken(expiresAt = Instant.now().plus(1, ChronoUnit.HOURS))

        assertTrue(token.isValid())
    }

    @Test
    fun `isValid should return false for expired token`() {
        val token = createToken(expiresAt = Instant.now().minus(1, ChronoUnit.HOURS))

        assertFalse(token.isValid())
    }

    @Test
    fun `isValid should return false for used token`() {
        val token = createToken(expiresAt = Instant.now().plus(1, ChronoUnit.HOURS))
            .apply { usedAt = Instant.now() }

        assertFalse(token.isValid())
    }

    @Test
    fun `markAsUsed should set usedAt timestamp`() {
        val token = createToken()

        token.markAsUsed()

        assertNotNull(token.usedAt)
        assertTrue(token.isUsed())
    }

    private fun createToken(
        expiresAt: Instant = Instant.now().plus(24, ChronoUnit.HOURS)
    ): VerificationToken {
        return VerificationToken(
            id = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            token = "test-token-abc123",
            expiresAt = expiresAt
        )
    }
}
