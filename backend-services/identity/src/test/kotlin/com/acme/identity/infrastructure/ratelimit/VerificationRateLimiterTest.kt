package com.acme.identity.infrastructure.ratelimit

import com.acme.identity.domain.VerificationResendRequest
import com.acme.identity.infrastructure.persistence.ResendRequestRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VerificationRateLimiterTest {

    private lateinit var resendRepository: ResendRequestRepository

    @BeforeEach
    fun setUp() {
        resendRepository = mockk()
    }

    @Test
    fun `checkRateLimit should return Allowed when under limit`() {
        // Given
        val rateLimiter = VerificationRateLimiter(
            resendRepository = resendRepository,
            enabled = true,
            maxRequestsPerHour = 3
        )
        every { resendRepository.countByEmailSince(any(), any()) } returns 1

        // When
        val result = rateLimiter.checkRateLimit("test@example.com")

        // Then
        assertIs<RateLimitResult.Allowed>(result)
        assertEquals(2, result.remaining)
    }

    @Test
    fun `checkRateLimit should return Exceeded when at limit`() {
        // Given
        val rateLimiter = VerificationRateLimiter(
            resendRepository = resendRepository,
            enabled = true,
            maxRequestsPerHour = 3
        )
        val oldestRequest = VerificationResendRequest(
            id = UUID.randomUUID(),
            email = "test@example.com",
            requestedAt = Instant.now().minusSeconds(1800)
        )
        every { resendRepository.countByEmailSince(any(), any()) } returns 3
        every { resendRepository.findOldestByEmailSince(any(), any()) } returns oldestRequest

        // When
        val result = rateLimiter.checkRateLimit("test@example.com")

        // Then
        assertIs<RateLimitResult.Exceeded>(result)
        assertEquals(0, result.remaining)
    }

    @Test
    fun `checkRateLimit should return Allowed when disabled regardless of request count`() {
        // Given - rate limiting disabled
        val rateLimiter = VerificationRateLimiter(
            resendRepository = resendRepository,
            enabled = false,
            maxRequestsPerHour = 3
        )

        // When - no mock setup needed, should not query database
        val result = rateLimiter.checkRateLimit("test@example.com")

        // Then
        assertIs<RateLimitResult.Allowed>(result)
        assertEquals(3, result.remaining)

        // Verify database was NOT queried
        verify(exactly = 0) { resendRepository.countByEmailSince(any(), any()) }
    }

    @Test
    fun `checkRateLimit should normalize email to lowercase`() {
        // Given
        val rateLimiter = VerificationRateLimiter(
            resendRepository = resendRepository,
            enabled = true,
            maxRequestsPerHour = 3
        )
        every { resendRepository.countByEmailSince("test@example.com", any()) } returns 0

        // When
        rateLimiter.checkRateLimit("TEST@EXAMPLE.COM")

        // Then - verify lowercase email was used
        verify { resendRepository.countByEmailSince("test@example.com", any()) }
    }

    @Test
    fun `getRateLimitInfo should return correct remaining count`() {
        // Given
        val rateLimiter = VerificationRateLimiter(
            resendRepository = resendRepository,
            enabled = true,
            maxRequestsPerHour = 3
        )
        every { resendRepository.countByEmailSince(any(), any()) } returns 2

        // When
        val (remaining, max) = rateLimiter.getRateLimitInfo("test@example.com")

        // Then
        assertEquals(1, remaining)
        assertEquals(3, max)
    }

    @Test
    fun `getRateLimitInfo should return zero remaining when exceeded`() {
        // Given
        val rateLimiter = VerificationRateLimiter(
            resendRepository = resendRepository,
            enabled = true,
            maxRequestsPerHour = 3
        )
        every { resendRepository.countByEmailSince(any(), any()) } returns 5

        // When
        val (remaining, max) = rateLimiter.getRateLimitInfo("test@example.com")

        // Then
        assertEquals(0, remaining) // Should be clamped to 0, not negative
        assertEquals(3, max)
    }
}
