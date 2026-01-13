package com.acme.customer.domain.events

import com.acme.customer.domain.ConsentSource
import com.acme.customer.domain.ConsentType
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ConsentGrantedTest {

    @Test
    fun `create should generate event with correct payload`() {
        // Given
        val customerId = UUID.randomUUID()
        val consentId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val grantedAt = Instant.now()
        val expiresAt = Instant.now().plus(365, ChronoUnit.DAYS)

        // When
        val event = ConsentGranted.create(
            customerId = customerId,
            consentId = consentId,
            consentType = ConsentType.MARKETING,
            grantedAt = grantedAt,
            source = ConsentSource.PROFILE_WIZARD,
            expiresAt = expiresAt,
            version = 1,
            correlationId = correlationId
        )

        // Then
        assertNotNull(event.eventId)
        assertNotNull(event.timestamp)
        assertEquals(ConsentGranted.EVENT_TYPE, event.eventType)
        assertEquals(ConsentGranted.EVENT_VERSION, event.eventVersion)
        assertEquals(ConsentGranted.AGGREGATE_TYPE, event.aggregateType)
        assertEquals(customerId, event.aggregateId)
        assertEquals(correlationId, event.correlationId)
        assertNull(event.causationId)

        assertEquals(customerId, event.payload.customerId)
        assertEquals(consentId, event.payload.consentId)
        assertEquals(ConsentType.MARKETING.name, event.payload.consentType)
        assertEquals(grantedAt, event.payload.grantedAt)
        assertEquals(ConsentSource.PROFILE_WIZARD.name, event.payload.source)
        assertEquals(expiresAt, event.payload.expiresAt)
        assertEquals(1, event.payload.version)
    }

    @Test
    fun `create should include causationId when provided`() {
        // Given
        val customerId = UUID.randomUUID()
        val consentId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val causationId = UUID.randomUUID()

        // When
        val event = ConsentGranted.create(
            customerId = customerId,
            consentId = consentId,
            consentType = ConsentType.ANALYTICS,
            grantedAt = Instant.now(),
            source = ConsentSource.API,
            expiresAt = null,
            version = 1,
            correlationId = correlationId,
            causationId = causationId
        )

        // Then
        assertEquals(causationId, event.causationId)
    }

    @Test
    fun `TOPIC should be customer events`() {
        assertEquals("customer.events", ConsentGranted.TOPIC)
    }

    @Test
    fun `EVENT_VERSION should be 1_0`() {
        assertEquals("1.0", ConsentGranted.EVENT_VERSION)
    }
}
