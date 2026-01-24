package com.acme.identity.domain.events

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.*

class UserLoggedInTest {

    @Test
    fun `create should generate event with correct type and version`() {
        val event = UserLoggedIn.create(
            userId = UUID.randomUUID(),
            sessionId = "sess_123",
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            mfaUsed = true,
            mfaMethod = "TOTP"
        )

        assertEquals("UserLoggedIn", event.eventType)
        assertEquals("1.0", event.eventVersion)
        assertEquals("User", event.aggregateType)
    }

    @Test
    fun `create should set payload fields correctly`() {
        val userId = UUID.randomUUID()
        val sessionId = "sess_123"
        val ipAddress = "192.168.1.1"
        val userAgent = "Mozilla/5.0"
        val deviceFingerprint = "fp_abc123"
        val mfaUsed = true
        val mfaMethod = "TOTP"
        val loginSource = "WEB"

        val event = UserLoggedIn.create(
            userId = userId,
            sessionId = sessionId,
            ipAddress = ipAddress,
            userAgent = userAgent,
            deviceFingerprint = deviceFingerprint,
            mfaUsed = mfaUsed,
            mfaMethod = mfaMethod,
            loginSource = loginSource
        )

        assertEquals(userId, event.payload.userId)
        assertEquals(sessionId, event.payload.sessionId)
        assertEquals(ipAddress, event.payload.ipAddress)
        assertEquals(userAgent, event.payload.userAgent)
        assertEquals(deviceFingerprint, event.payload.deviceFingerprint)
        assertEquals(mfaUsed, event.payload.mfaUsed)
        assertEquals(mfaMethod, event.payload.mfaMethod)
        assertEquals(loginSource, event.payload.loginSource)
    }

    @Test
    fun `create should handle null device fingerprint`() {
        val event = UserLoggedIn.create(
            userId = UUID.randomUUID(),
            sessionId = "sess_123",
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            deviceFingerprint = null,
            mfaUsed = false,
            mfaMethod = null
        )

        assertNull(event.payload.deviceFingerprint)
    }

    @Test
    fun `create should handle null MFA method when MFA not used`() {
        val event = UserLoggedIn.create(
            userId = UUID.randomUUID(),
            sessionId = "sess_123",
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            mfaUsed = false,
            mfaMethod = null
        )

        assertFalse(event.payload.mfaUsed)
        assertNull(event.payload.mfaMethod)
    }

    @Test
    fun `create should use default login source WEB`() {
        val event = UserLoggedIn.create(
            userId = UUID.randomUUID(),
            sessionId = "sess_123",
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            mfaUsed = true,
            mfaMethod = "TOTP"
        )

        assertEquals("WEB", event.payload.loginSource)
    }

    @Test
    fun `TOPIC constant should be correct`() {
        assertEquals("identity.authentication.events", UserLoggedIn.TOPIC)
    }

    @Test
    fun `create should accept custom correlation ID`() {
        val correlationId = UUID.randomUUID()

        val event = UserLoggedIn.create(
            userId = UUID.randomUUID(),
            sessionId = "sess_123",
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            mfaUsed = true,
            mfaMethod = "TOTP",
            correlationId = correlationId
        )

        assertEquals(correlationId, event.correlationId)
    }
}
