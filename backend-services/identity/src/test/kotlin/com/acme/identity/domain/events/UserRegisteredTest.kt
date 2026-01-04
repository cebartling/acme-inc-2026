package com.acme.identity.domain.events

import com.acme.identity.domain.RegistrationSource
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UserRegisteredTest {

    @Test
    fun `create should generate event with correct properties`() {
        val userId = UUID.randomUUID()
        val email = "test@example.com"
        val firstName = "Jane"
        val lastName = "Doe"
        val tosAcceptedAt = Instant.now()
        val marketingOptIn = true
        val registrationSource = RegistrationSource.WEB

        val event = UserRegistered.create(
            userId = userId,
            email = email,
            firstName = firstName,
            lastName = lastName,
            tosAcceptedAt = tosAcceptedAt,
            marketingOptIn = marketingOptIn,
            registrationSource = registrationSource
        )

        assertEquals(UserRegistered.EVENT_TYPE, event.eventType)
        assertEquals(UserRegistered.EVENT_VERSION, event.eventVersion)
        assertEquals(UserRegistered.AGGREGATE_TYPE, event.aggregateType)
        assertEquals(userId, event.aggregateId)
        assertNotNull(event.eventId)
        assertNotNull(event.timestamp)
        assertNotNull(event.correlationId)
    }

    @Test
    fun `create should populate payload correctly`() {
        val userId = UUID.randomUUID()
        val email = "test@example.com"
        val firstName = "Jane"
        val lastName = "Doe"
        val tosAcceptedAt = Instant.now()
        val marketingOptIn = true
        val registrationSource = RegistrationSource.MOBILE

        val event = UserRegistered.create(
            userId = userId,
            email = email,
            firstName = firstName,
            lastName = lastName,
            tosAcceptedAt = tosAcceptedAt,
            marketingOptIn = marketingOptIn,
            registrationSource = registrationSource
        )

        assertEquals(userId, event.payload.userId)
        assertEquals(email, event.payload.email)
        assertEquals(firstName, event.payload.firstName)
        assertEquals(lastName, event.payload.lastName)
        assertEquals(tosAcceptedAt, event.payload.tosAcceptedAt)
        assertEquals(marketingOptIn, event.payload.marketingOptIn)
        assertEquals(registrationSource, event.payload.registrationSource)
    }

    @Test
    fun `create should use provided correlation ID`() {
        val correlationId = UUID.randomUUID()

        val event = UserRegistered.create(
            userId = UUID.randomUUID(),
            email = "test@example.com",
            firstName = "Jane",
            lastName = "Doe",
            tosAcceptedAt = Instant.now(),
            marketingOptIn = false,
            registrationSource = RegistrationSource.API,
            correlationId = correlationId
        )

        assertEquals(correlationId, event.correlationId)
    }

    @Test
    fun `TOPIC constant should be correct`() {
        assertEquals("identity.user.events", UserRegistered.TOPIC)
    }
}
