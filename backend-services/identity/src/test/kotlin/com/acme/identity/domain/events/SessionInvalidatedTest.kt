package com.acme.identity.domain.events

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class SessionInvalidatedTest {

    @Test
    fun `create should generate event with correct type and version`() {
        val event = SessionInvalidated.create(
            sessionId = "sess_${UUID.randomUUID()}",
            userId = UUID.randomUUID(),
            reason = SessionInvalidated.REASON_LOGOUT
        )

        assertEquals("SessionInvalidated", event.eventType)
        assertEquals("1.0", event.eventVersion)
        assertEquals("Session", event.aggregateType)
    }

    @Test
    fun `create should set payload fields correctly`() {
        val sessionId = "sess_${UUID.randomUUID()}"
        val userId = UUID.randomUUID()
        val reason = SessionInvalidated.REASON_LOGOUT

        val event = SessionInvalidated.create(
            sessionId = sessionId,
            userId = userId,
            reason = reason
        )

        assertEquals(sessionId, event.payload.sessionId)
        assertEquals(userId, event.payload.userId)
        assertEquals(reason, event.payload.reason)
    }

    @Test
    fun `create should set invalidatedAt to current time`() {
        val before = Instant.now()
        val event = SessionInvalidated.create(
            sessionId = "sess_${UUID.randomUUID()}",
            userId = UUID.randomUUID(),
            reason = SessionInvalidated.REASON_LOGOUT
        )
        val after = Instant.now()

        assertTrue(event.payload.invalidatedAt.isAfter(before.minusSeconds(1)))
        assertTrue(event.payload.invalidatedAt.isBefore(after.plusSeconds(1)))
    }

    @Test
    fun `reason constants should be defined correctly`() {
        assertEquals("CONCURRENT_SESSION_LIMIT", SessionInvalidated.REASON_CONCURRENT_LIMIT)
        assertEquals("LOGOUT", SessionInvalidated.REASON_LOGOUT)
        assertEquals("EXPIRED", SessionInvalidated.REASON_EXPIRED)
        assertEquals("SECURITY", SessionInvalidated.REASON_SECURITY)
    }

    @Test
    fun `TOPIC constant should be correct`() {
        assertEquals("identity.session.events", SessionInvalidated.TOPIC)
    }

    @Test
    fun `create should accept custom correlation ID`() {
        val correlationId = UUID.randomUUID()

        val event = SessionInvalidated.create(
            sessionId = "sess_${UUID.randomUUID()}",
            userId = UUID.randomUUID(),
            reason = SessionInvalidated.REASON_SECURITY,
            correlationId = correlationId
        )

        assertEquals(correlationId, event.correlationId)
    }

    @Test
    fun `create should work with all reason constants`() {
        val userId = UUID.randomUUID()
        val reasons = listOf(
            SessionInvalidated.REASON_CONCURRENT_LIMIT,
            SessionInvalidated.REASON_LOGOUT,
            SessionInvalidated.REASON_EXPIRED,
            SessionInvalidated.REASON_SECURITY
        )

        reasons.forEach { reason ->
            val event = SessionInvalidated.create(
                sessionId = "sess_${UUID.randomUUID()}",
                userId = userId,
                reason = reason
            )

            assertEquals(reason, event.payload.reason)
        }
    }
}
