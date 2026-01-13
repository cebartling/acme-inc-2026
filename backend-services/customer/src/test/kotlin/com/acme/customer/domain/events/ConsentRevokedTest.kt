package com.acme.customer.domain.events

import com.acme.customer.domain.ConsentSource
import com.acme.customer.domain.ConsentType
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ConsentRevokedTest {

    @Test
    fun `create should generate event with correct payload`() {
        // Given
        val customerId = UUID.randomUUID()
        val consentId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val revokedAt = Instant.now()

        // When
        val event = ConsentRevoked.create(
            customerId = customerId,
            consentId = consentId,
            consentType = ConsentType.MARKETING,
            revokedAt = revokedAt,
            source = ConsentSource.PRIVACY_SETTINGS,
            version = 2,
            correlationId = correlationId
        )

        // Then
        assertNotNull(event.eventId)
        assertNotNull(event.timestamp)
        assertEquals(ConsentRevoked.EVENT_TYPE, event.eventType)
        assertEquals(ConsentRevoked.EVENT_VERSION, event.eventVersion)
        assertEquals(ConsentRevoked.AGGREGATE_TYPE, event.aggregateType)
        assertEquals(customerId, event.aggregateId)
        assertEquals(correlationId, event.correlationId)
        assertNull(event.causationId)

        assertEquals(customerId, event.payload.customerId)
        assertEquals(consentId, event.payload.consentId)
        assertEquals(ConsentType.MARKETING.name, event.payload.consentType)
        assertEquals(revokedAt, event.payload.revokedAt)
        assertEquals(ConsentSource.PRIVACY_SETTINGS.name, event.payload.source)
        assertEquals(2, event.payload.version)
    }

    @Test
    fun `create should include causationId when provided`() {
        // Given
        val customerId = UUID.randomUUID()
        val consentId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val causationId = UUID.randomUUID()

        // When
        val event = ConsentRevoked.create(
            customerId = customerId,
            consentId = consentId,
            consentType = ConsentType.THIRD_PARTY,
            revokedAt = Instant.now(),
            source = ConsentSource.API,
            version = 3,
            correlationId = correlationId,
            causationId = causationId
        )

        // Then
        assertEquals(causationId, event.causationId)
    }

    @Test
    fun `TOPIC should be customer events`() {
        assertEquals("customer.events", ConsentRevoked.TOPIC)
    }

    @Test
    fun `EVENT_VERSION should be 1_0`() {
        assertEquals("1.0", ConsentRevoked.EVENT_VERSION)
    }
}
