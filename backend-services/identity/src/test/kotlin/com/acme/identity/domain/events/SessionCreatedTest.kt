package com.acme.identity.domain.events

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class SessionCreatedTest {

    @Test
    fun `create should generate event with correct type and version`() {
        val event = SessionCreated.create(
            sessionId = "sess_${UUID.randomUUID()}",
            userId = UUID.randomUUID(),
            deviceId = "dev_123",
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            expiresAt = Instant.now().plusSeconds(3600)
        )

        assertEquals("SessionCreated", event.eventType)
        assertEquals("1.0", event.eventVersion)
        assertEquals("Session", event.aggregateType)
    }

    @Test
    fun `create should set payload fields correctly`() {
        val sessionId = "sess_${UUID.randomUUID()}"
        val userId = UUID.randomUUID()
        val deviceId = "dev_123"
        val ipAddress = "192.168.1.1"
        val userAgent = "Mozilla/5.0"
        val expiresAt = Instant.now().plusSeconds(3600)

        val event = SessionCreated.create(
            sessionId = sessionId,
            userId = userId,
            deviceId = deviceId,
            ipAddress = ipAddress,
            userAgent = userAgent,
            expiresAt = expiresAt
        )

        assertEquals(sessionId, event.payload.sessionId)
        assertEquals(userId, event.payload.userId)
        assertEquals(deviceId, event.payload.deviceId)
        assertEquals(ipAddress, event.payload.ipAddress)
        assertEquals(userAgent, event.payload.userAgent)
        assertEquals(expiresAt, event.payload.expiresAt)
    }

    @Test
    fun `create should generate unique event ID`() {
        val event1 = SessionCreated.create(
            sessionId = "sess_${UUID.randomUUID()}",
            userId = UUID.randomUUID(),
            deviceId = "dev_123",
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            expiresAt = Instant.now().plusSeconds(3600)
        )

        val event2 = SessionCreated.create(
            sessionId = "sess_${UUID.randomUUID()}",
            userId = UUID.randomUUID(),
            deviceId = "dev_456",
            ipAddress = "192.168.1.2",
            userAgent = "Chrome/91.0",
            expiresAt = Instant.now().plusSeconds(3600)
        )

        assertNotEquals(event1.eventId, event2.eventId)
    }

    @Test
    fun `create should set timestamp to current time`() {
        val before = Instant.now()
        val event = SessionCreated.create(
            sessionId = "sess_${UUID.randomUUID()}",
            userId = UUID.randomUUID(),
            deviceId = "dev_123",
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            expiresAt = Instant.now().plusSeconds(3600)
        )
        val after = Instant.now()

        assertTrue(event.timestamp.isAfter(before.minusSeconds(1)))
        assertTrue(event.timestamp.isBefore(after.plusSeconds(1)))
    }

    @Test
    fun `TOPIC constant should be correct`() {
        assertEquals("identity.session.events", SessionCreated.TOPIC)
    }

    @Test
    fun `create should accept custom correlation ID`() {
        val correlationId = UUID.randomUUID()

        val event = SessionCreated.create(
            sessionId = "sess_${UUID.randomUUID()}",
            userId = UUID.randomUUID(),
            deviceId = "dev_123",
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            expiresAt = Instant.now().plusSeconds(3600),
            correlationId = correlationId
        )

        assertEquals(correlationId, event.correlationId)
    }
}
