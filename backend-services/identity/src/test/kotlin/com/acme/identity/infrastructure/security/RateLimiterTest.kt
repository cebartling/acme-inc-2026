package com.acme.identity.infrastructure.security

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class RateLimiterTest {

    @Nested
    @DisplayName("When rate limiting is enabled")
    inner class WhenEnabled {

        private lateinit var rateLimiter: RateLimiter

        @BeforeEach
        fun setUp() {
            rateLimiter = RateLimiter(enabled = true, requestsPerMinute = 5)
        }

        @Test
        fun `tryAcquire should allow requests within limit`() {
            val ip = "192.168.1.1"

            // First 5 requests should succeed
            repeat(5) {
                assertTrue(rateLimiter.tryAcquire(ip))
            }
        }

        @Test
        fun `tryAcquire should reject requests exceeding limit`() {
            val ip = "192.168.1.2"

            // Exhaust the limit
            repeat(5) {
                rateLimiter.tryAcquire(ip)
            }

            // 6th request should fail
            assertFalse(rateLimiter.tryAcquire(ip))
        }

        @Test
        fun `tryAcquire should track different IPs independently`() {
            val ip1 = "192.168.1.3"
            val ip2 = "192.168.1.4"

            // Exhaust limit for ip1
            repeat(5) {
                rateLimiter.tryAcquire(ip1)
            }

            // ip2 should still have its full allowance
            assertTrue(rateLimiter.tryAcquire(ip2))
        }

        @Test
        fun `reset should allow new requests after reset`() {
            val ip = "192.168.1.5"

            // Exhaust the limit
            repeat(5) {
                rateLimiter.tryAcquire(ip)
            }

            // Should be rejected
            assertFalse(rateLimiter.tryAcquire(ip))

            // Reset the bucket
            rateLimiter.reset(ip)

            // Should be allowed again
            assertTrue(rateLimiter.tryAcquire(ip))
        }
    }

    @Nested
    @DisplayName("When rate limiting is disabled")
    inner class WhenDisabled {

        private lateinit var rateLimiter: RateLimiter

        @BeforeEach
        fun setUp() {
            rateLimiter = RateLimiter(enabled = false, requestsPerMinute = 5)
        }

        @Test
        fun `tryAcquire should always allow requests when disabled`() {
            val ip = "192.168.1.10"

            // Should allow unlimited requests when disabled
            repeat(100) {
                assertTrue(rateLimiter.tryAcquire(ip))
            }
        }

        @Test
        fun `tryAcquire should allow requests from same IP without limit`() {
            val ip = "192.168.1.11"

            // Even after many requests, should still succeed
            repeat(50) {
                rateLimiter.tryAcquire(ip)
            }

            // Next request should still succeed
            assertTrue(rateLimiter.tryAcquire(ip))
        }
    }
}
