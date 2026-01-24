package com.acme.identity.domain

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class DeviceTrustTest {

    private val testUserId = UUID.randomUUID()
    private val testFingerprint = "fp_test123"
    private val testUserAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)"
    private val testIpAddress = "192.168.1.100"

    @Test
    fun `create should generate device trust with trust_ prefix`() {
        val deviceTrust = DeviceTrust.create(
            userId = testUserId,
            deviceFingerprint = testFingerprint,
            userAgent = testUserAgent,
            ipAddress = testIpAddress
        )

        assertTrue(deviceTrust.id.startsWith("trust_"))
    }

    @Test
    fun `create should hash the device fingerprint with SHA-256`() {
        val deviceTrust = DeviceTrust.create(
            userId = testUserId,
            deviceFingerprint = testFingerprint,
            userAgent = testUserAgent,
            ipAddress = testIpAddress
        )

        // Verify fingerprint is hashed (should be 64 hex characters for SHA-256)
        assertEquals(64, deviceTrust.deviceFingerprint.length)
        assertNotEquals(testFingerprint, deviceTrust.deviceFingerprint)
        // Verify it contains only hex characters
        assertTrue(deviceTrust.deviceFingerprint.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `create should set default TTL to 30 days`() {
        val deviceTrust = DeviceTrust.create(
            userId = testUserId,
            deviceFingerprint = testFingerprint,
            userAgent = testUserAgent,
            ipAddress = testIpAddress
        )

        assertEquals(2592000L, deviceTrust.ttl) // 30 days in seconds
    }

    @Test
    fun `create should set expiresAt to 30 days from now`() {
        val before = Instant.now()
        val deviceTrust = DeviceTrust.create(
            userId = testUserId,
            deviceFingerprint = testFingerprint,
            userAgent = testUserAgent,
            ipAddress = testIpAddress
        )
        val after = Instant.now()

        val expected30DaysFromNow = before.plusSeconds(2592000L)
        assertTrue(deviceTrust.expiresAt.isAfter(expected30DaysFromNow.minusSeconds(2)))
        assertTrue(deviceTrust.expiresAt.isBefore(expected30DaysFromNow.plusSeconds(2)))
    }

    @Test
    fun `create should set createdAt and lastUsedAt to now`() {
        val before = Instant.now()
        val deviceTrust = DeviceTrust.create(
            userId = testUserId,
            deviceFingerprint = testFingerprint,
            userAgent = testUserAgent,
            ipAddress = testIpAddress
        )
        val after = Instant.now()

        assertTrue(deviceTrust.createdAt.isAfter(before.minusSeconds(1)))
        assertTrue(deviceTrust.createdAt.isBefore(after.plusSeconds(1)))
        assertEquals(deviceTrust.createdAt, deviceTrust.lastUsedAt)
    }

    @Test
    fun `matches should return true when fingerprint and userAgent match`() {
        val deviceTrust = DeviceTrust.create(
            userId = testUserId,
            deviceFingerprint = testFingerprint,
            userAgent = testUserAgent,
            ipAddress = testIpAddress
        )

        assertTrue(deviceTrust.matches(testFingerprint, testUserAgent))
    }

    @Test
    fun `matches should return false when fingerprint does not match`() {
        val deviceTrust = DeviceTrust.create(
            userId = testUserId,
            deviceFingerprint = testFingerprint,
            userAgent = testUserAgent,
            ipAddress = testIpAddress
        )

        assertFalse(deviceTrust.matches("different_fingerprint", testUserAgent))
    }

    @Test
    fun `matches should return false when userAgent does not match`() {
        val deviceTrust = DeviceTrust.create(
            userId = testUserId,
            deviceFingerprint = testFingerprint,
            userAgent = testUserAgent,
            ipAddress = testIpAddress
        )

        assertFalse(deviceTrust.matches(testFingerprint, "Different User Agent"))
    }

    @Test
    fun `isExpired should return false for non-expired trust`() {
        val deviceTrust = DeviceTrust.create(
            userId = testUserId,
            deviceFingerprint = testFingerprint,
            userAgent = testUserAgent,
            ipAddress = testIpAddress
        )

        assertFalse(deviceTrust.isExpired())
    }

    @Test
    fun `isExpired should return true for expired trust`() {
        val deviceTrust = DeviceTrust.create(
            userId = testUserId,
            deviceFingerprint = testFingerprint,
            userAgent = testUserAgent,
            ipAddress = testIpAddress,
            ttlSeconds = 0L // Expired immediately
        )

        // Wait a moment to ensure it's expired
        Thread.sleep(10)
        assertTrue(deviceTrust.isExpired())
    }

    @Test
    fun `touch should update lastUsedAt to now`() {
        val deviceTrust = DeviceTrust.create(
            userId = testUserId,
            deviceFingerprint = testFingerprint,
            userAgent = testUserAgent,
            ipAddress = testIpAddress
        )

        val originalLastUsedAt = deviceTrust.lastUsedAt
        Thread.sleep(10) // Small delay to ensure time difference

        deviceTrust.touch()

        assertTrue(deviceTrust.lastUsedAt.isAfter(originalLastUsedAt))
    }

    @Test
    fun `parseDeviceName should extract Chrome on macOS`() {
        val userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        val deviceTrust = DeviceTrust.create(
            userId = testUserId,
            deviceFingerprint = testFingerprint,
            userAgent = userAgent,
            ipAddress = testIpAddress
        )

        assertEquals("Chrome on macOS", deviceTrust.parseDeviceName())
    }

    @Test
    fun `parseDeviceName should extract Firefox on Windows`() {
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:89.0) Gecko/20100101 Firefox/89.0"
        val deviceTrust = DeviceTrust.create(
            userId = testUserId,
            deviceFingerprint = testFingerprint,
            userAgent = userAgent,
            ipAddress = testIpAddress
        )

        assertEquals("Firefox on Windows", deviceTrust.parseDeviceName())
    }

    @Test
    fun `parseDeviceName should extract Safari on iOS`() {
        val userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 14_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Mobile/15E148 Safari/604.1"
        val deviceTrust = DeviceTrust.create(
            userId = testUserId,
            deviceFingerprint = testFingerprint,
            userAgent = userAgent,
            ipAddress = testIpAddress
        )

        assertEquals("Safari on iOS", deviceTrust.parseDeviceName())
    }

    @Test
    fun `parseDeviceName should extract Chrome on Android`() {
        val userAgent = "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"
        val deviceTrust = DeviceTrust.create(
            userId = testUserId,
            deviceFingerprint = testFingerprint,
            userAgent = userAgent,
            ipAddress = testIpAddress
        )

        assertEquals("Chrome on Android", deviceTrust.parseDeviceName())
    }

    @Test
    fun `parseDeviceName should handle unknown browser and OS`() {
        val userAgent = "CustomBrowser/1.0"
        val deviceTrust = DeviceTrust.create(
            userId = testUserId,
            deviceFingerprint = testFingerprint,
            userAgent = userAgent,
            ipAddress = testIpAddress
        )

        assertEquals("Unknown Browser on Unknown OS", deviceTrust.parseDeviceName())
    }

    @Test
    fun `same fingerprint should produce same hash`() {
        val trust1 = DeviceTrust.create(
            userId = testUserId,
            deviceFingerprint = testFingerprint,
            userAgent = testUserAgent,
            ipAddress = testIpAddress
        )

        val trust2 = DeviceTrust.create(
            userId = testUserId,
            deviceFingerprint = testFingerprint,
            userAgent = testUserAgent,
            ipAddress = testIpAddress
        )

        assertEquals(trust1.deviceFingerprint, trust2.deviceFingerprint)
    }

    @Test
    fun `different fingerprints should produce different hashes`() {
        val trust1 = DeviceTrust.create(
            userId = testUserId,
            deviceFingerprint = "fingerprint1",
            userAgent = testUserAgent,
            ipAddress = testIpAddress
        )

        val trust2 = DeviceTrust.create(
            userId = testUserId,
            deviceFingerprint = "fingerprint2",
            userAgent = testUserAgent,
            ipAddress = testIpAddress
        )

        assertNotEquals(trust1.deviceFingerprint, trust2.deviceFingerprint)
    }
}
