package com.acme.identity.domain.events

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EmailVerifiedTest {

    @Test
    fun `create should generate event with correct properties`() {
        // Given
        val userId = UUID.randomUUID()
        val email = "customer@example.com"
        val verifiedAt = Instant.now()
        val correlationId = UUID.randomUUID()

        // When
        val event = EmailVerified.create(
            userId = userId,
            email = email,
            verifiedAt = verifiedAt,
            correlationId = correlationId
        )

        // Then
        assertEquals(EmailVerified.EVENT_TYPE, event.eventType)
        assertEquals(EmailVerified.EVENT_VERSION, event.eventVersion)
        assertEquals(EmailVerified.AGGREGATE_TYPE, event.aggregateType)
        assertEquals(userId, event.aggregateId)
        assertEquals(correlationId, event.correlationId)
        assertNotNull(event.eventId)
        assertNotNull(event.timestamp)

        assertEquals(userId, event.payload.userId)
        assertEquals(email, event.payload.email)
        assertEquals(verifiedAt, event.payload.verifiedAt)
    }

    @Test
    fun `create should generate unique event IDs`() {
        // Given
        val userId = UUID.randomUUID()

        // When
        val event1 = EmailVerified.create(userId = userId, email = "a@example.com")
        val event2 = EmailVerified.create(userId = userId, email = "b@example.com")

        // Then
        assertNotNull(event1.eventId)
        assertNotNull(event2.eventId)
        assert(event1.eventId != event2.eventId)
    }

    @Test
    fun `create should use default verifiedAt when not specified`() {
        // Given
        val userId = UUID.randomUUID()
        val before = Instant.now()

        // When
        val event = EmailVerified.create(userId = userId, email = "test@example.com")
        val after = Instant.now()

        // Then
        assertNotNull(event.payload.verifiedAt)
        assert(!event.payload.verifiedAt.isBefore(before))
        assert(!event.payload.verifiedAt.isAfter(after))
    }

    @Test
    fun `constants should have correct values`() {
        assertEquals("EmailVerified", EmailVerified.EVENT_TYPE)
        assertEquals("1.0", EmailVerified.EVENT_VERSION)
        assertEquals("User", EmailVerified.AGGREGATE_TYPE)
        assertEquals("identity.user.events", EmailVerified.TOPIC)
    }
}
