package com.acme.identity.domain

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class SessionTest {

    @Test
    fun `create should generate session with sess_ prefix`() {
        val session = Session.create(
            userId = UUID.randomUUID(),
            deviceId = "dev_123",
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            tokenFamily = "fam_123"
        )

        assertTrue(session.id.startsWith("sess_"))
    }

    @Test
    fun `create should set all properties correctly`() {
        val userId = UUID.randomUUID()
        val deviceId = "dev_123"
        val ipAddress = "192.168.1.1"
        val userAgent = "Mozilla/5.0"
        val tokenFamily = "fam_123"

        val session = Session.create(
            userId = userId,
            deviceId = deviceId,
            ipAddress = ipAddress,
            userAgent = userAgent,
            tokenFamily = tokenFamily
        )

        assertEquals(userId, session.userId)
        assertEquals(deviceId, session.deviceId)
        assertEquals(ipAddress, session.ipAddress)
        assertEquals(userAgent, session.userAgent)
        assertEquals(tokenFamily, session.tokenFamily)
    }

    @Test
    fun `create should set default TTL to 7 days`() {
        val session = Session.create(
            userId = UUID.randomUUID(),
            deviceId = "dev_123",
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            tokenFamily = "fam_123"
        )

        assertEquals(604800L, session.ttl) // 7 days in seconds
    }

    @Test
    fun `create should accept custom TTL`() {
        val customTtl = 3600L // 1 hour

        val session = Session.create(
            userId = UUID.randomUUID(),
            deviceId = "dev_123",
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            tokenFamily = "fam_123",
            ttlSeconds = customTtl
        )

        assertEquals(customTtl, session.ttl)
    }

    @Test
    fun `create should set createdAt to current time`() {
        val before = Instant.now()
        val session = Session.create(
            userId = UUID.randomUUID(),
            deviceId = "dev_123",
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            tokenFamily = "fam_123"
        )
        val after = Instant.now()

        assertTrue(session.createdAt.isAfter(before.minusSeconds(1)))
        assertTrue(session.createdAt.isBefore(after.plusSeconds(1)))
    }

    @Test
    fun `create should set expiresAt based on TTL`() {
        val session = Session.create(
            userId = UUID.randomUUID(),
            deviceId = "dev_123",
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            tokenFamily = "fam_123",
            ttlSeconds = 3600
        )

        val expectedExpiry = session.createdAt.plusSeconds(3600)
        assertEquals(expectedExpiry, session.expiresAt)
    }

    @Test
    fun `create should generate unique session IDs`() {
        val session1 = Session.create(
            userId = UUID.randomUUID(),
            deviceId = "dev_123",
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            tokenFamily = "fam_123"
        )

        val session2 = Session.create(
            userId = UUID.randomUUID(),
            deviceId = "dev_123",
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            tokenFamily = "fam_123"
        )

        assertNotEquals(session1.id, session2.id)
    }

    @Test
    fun `session ID should be valid UUID format after prefix`() {
        val session = Session.create(
            userId = UUID.randomUUID(),
            deviceId = "dev_123",
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            tokenFamily = "fam_123"
        )

        val uuidPart = session.id.removePrefix("sess_")
        assertNotNull(UUID.fromString(uuidPart))
    }
}
