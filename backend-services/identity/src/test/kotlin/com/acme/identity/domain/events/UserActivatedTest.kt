package com.acme.identity.domain.events

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UserActivatedTest {

    @Test
    fun `create should generate event with correct properties`() {
        // Given
        val userId = UUID.randomUUID()
        val activatedAt = Instant.now()
        val activationMethod = ActivationMethod.EMAIL_VERIFICATION
        val correlationId = UUID.randomUUID()

        // When
        val event = UserActivated.create(
            userId = userId,
            activatedAt = activatedAt,
            activationMethod = activationMethod,
            correlationId = correlationId
        )

        // Then
        assertEquals(UserActivated.EVENT_TYPE, event.eventType)
        assertEquals(UserActivated.EVENT_VERSION, event.eventVersion)
        assertEquals(UserActivated.AGGREGATE_TYPE, event.aggregateType)
        assertEquals(userId, event.aggregateId)
        assertEquals(correlationId, event.correlationId)
        assertNotNull(event.eventId)
        assertNotNull(event.timestamp)

        assertEquals(userId, event.payload.userId)
        assertEquals(activatedAt, event.payload.activatedAt)
        assertEquals(activationMethod, event.payload.activationMethod)
    }

    @Test
    fun `create should generate unique event IDs`() {
        // Given
        val userId = UUID.randomUUID()

        // When
        val event1 = UserActivated.create(userId = userId)
        val event2 = UserActivated.create(userId = userId)

        // Then
        assertNotNull(event1.eventId)
        assertNotNull(event2.eventId)
        assert(event1.eventId != event2.eventId)
    }

    @Test
    fun `create should use default values when not specified`() {
        // Given
        val userId = UUID.randomUUID()
        val before = Instant.now()

        // When
        val event = UserActivated.create(userId = userId)
        val after = Instant.now()

        // Then
        assertNotNull(event.payload.activatedAt)
        assert(!event.payload.activatedAt.isBefore(before))
        assert(!event.payload.activatedAt.isAfter(after))
        assertEquals(ActivationMethod.EMAIL_VERIFICATION, event.payload.activationMethod)
    }

    @Test
    fun `constants should have correct values`() {
        assertEquals("UserActivated", UserActivated.EVENT_TYPE)
        assertEquals("1.0", UserActivated.EVENT_VERSION)
        assertEquals("User", UserActivated.AGGREGATE_TYPE)
        assertEquals("identity.user.events", UserActivated.TOPIC)
    }

    @Test
    fun `ActivationMethod enum should have all expected values`() {
        val methods = ActivationMethod.entries.toTypedArray()
        assertEquals(3, methods.size)
        assert(methods.contains(ActivationMethod.EMAIL_VERIFICATION))
        assert(methods.contains(ActivationMethod.ADMIN_ACTIVATION))
        assert(methods.contains(ActivationMethod.PHONE_VERIFICATION))
    }
}
