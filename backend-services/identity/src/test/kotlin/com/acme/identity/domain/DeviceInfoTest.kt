package com.acme.identity.domain

import org.junit.jupiter.api.Test
import kotlin.test.*

class DeviceInfoTest {

    @Test
    fun `fromRequest should use provided fingerprint when available`() {
        val fingerprint = "fp_abc123xyz789"
        val deviceInfo = DeviceInfo.fromRequest(
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            fingerprint = fingerprint
        )

        assertEquals(fingerprint, deviceInfo.deviceId)
        assertEquals(fingerprint, deviceInfo.fingerprint)
    }

    @Test
    fun `fromRequest should generate device ID when fingerprint is null`() {
        val deviceInfo = DeviceInfo.fromRequest(
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            fingerprint = null
        )

        assertTrue(deviceInfo.deviceId.startsWith("dev_"))
        assertNull(deviceInfo.fingerprint)
    }

    @Test
    fun `fromRequest should set IP address and user agent`() {
        val ipAddress = "192.168.1.1"
        val userAgent = "Mozilla/5.0"

        val deviceInfo = DeviceInfo.fromRequest(
            ipAddress = ipAddress,
            userAgent = userAgent
        )

        assertEquals(ipAddress, deviceInfo.ipAddress)
        assertEquals(userAgent, deviceInfo.userAgent)
    }

    @Test
    fun `generated device ID should be consistent for same IP and user agent`() {
        val ipAddress = "192.168.1.1"
        val userAgent = "Mozilla/5.0"

        val deviceInfo1 = DeviceInfo.fromRequest(
            ipAddress = ipAddress,
            userAgent = userAgent
        )

        val deviceInfo2 = DeviceInfo.fromRequest(
            ipAddress = ipAddress,
            userAgent = userAgent
        )

        assertEquals(deviceInfo1.deviceId, deviceInfo2.deviceId)
    }

    @Test
    fun `generated device ID should differ for different IP`() {
        val userAgent = "Mozilla/5.0"

        val deviceInfo1 = DeviceInfo.fromRequest(
            ipAddress = "192.168.1.1",
            userAgent = userAgent
        )

        val deviceInfo2 = DeviceInfo.fromRequest(
            ipAddress = "192.168.1.2",
            userAgent = userAgent
        )

        assertNotEquals(deviceInfo1.deviceId, deviceInfo2.deviceId)
    }

    @Test
    fun `generated device ID should differ for different user agent`() {
        val ipAddress = "192.168.1.1"

        val deviceInfo1 = DeviceInfo.fromRequest(
            ipAddress = ipAddress,
            userAgent = "Mozilla/5.0"
        )

        val deviceInfo2 = DeviceInfo.fromRequest(
            ipAddress = ipAddress,
            userAgent = "Chrome/91.0"
        )

        assertNotEquals(deviceInfo1.deviceId, deviceInfo2.deviceId)
    }

    @Test
    fun `generated device ID should have dev_ prefix`() {
        val deviceInfo = DeviceInfo.fromRequest(
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0"
        )

        assertTrue(deviceInfo.deviceId.startsWith("dev_"))
    }
}
