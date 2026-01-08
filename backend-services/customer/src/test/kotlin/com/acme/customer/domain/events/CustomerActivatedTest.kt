package com.acme.customer.domain.events

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CustomerActivatedTest {

    @Test
    fun `create should generate event with correct properties`() {
        // Given
        val customerId = UUID.randomUUID()
        val activatedAt = Instant.now()
        val emailVerified = true
        val correlationId = UUID.randomUUID()
        val causationId = UUID.randomUUID()

        // When
        val event = CustomerActivated.create(
            customerId = customerId,
            activatedAt = activatedAt,
            emailVerified = emailVerified,
            correlationId = correlationId,
            causationId = causationId
        )

        // Then
        assertNotNull(event.eventId)
        assertEquals(CustomerActivated.EVENT_TYPE, event.eventType)
        assertEquals(CustomerActivated.EVENT_VERSION, event.eventVersion)
        assertNotNull(event.timestamp)
        assertEquals(customerId, event.aggregateId)
        assertEquals(CustomerActivated.AGGREGATE_TYPE, event.aggregateType)
        assertEquals(correlationId, event.correlationId)
        assertEquals(causationId, event.causationId)

        // Verify payload
        assertEquals(customerId, event.payload.customerId)
        assertEquals(activatedAt, event.payload.activatedAt)
        assertTrue(event.payload.emailVerified)
    }

    @Test
    fun `event type should be CustomerActivated`() {
        assertEquals("CustomerActivated", CustomerActivated.EVENT_TYPE)
    }

    @Test
    fun `event version should be 1_0`() {
        assertEquals("1.0", CustomerActivated.EVENT_VERSION)
    }

    @Test
    fun `aggregate type should be Customer`() {
        assertEquals("Customer", CustomerActivated.AGGREGATE_TYPE)
    }

    @Test
    fun `topic should be customer_events`() {
        assertEquals("customer.events", CustomerActivated.TOPIC)
    }

    @Test
    fun `create should generate unique event IDs for each call`() {
        // Given
        val customerId = UUID.randomUUID()
        val activatedAt = Instant.now()
        val correlationId = UUID.randomUUID()
        val causationId = UUID.randomUUID()

        // When
        val event1 = CustomerActivated.create(customerId, activatedAt, true, correlationId, causationId)
        val event2 = CustomerActivated.create(customerId, activatedAt, true, correlationId, causationId)

        // Then
        assertTrue(event1.eventId != event2.eventId)
    }

    @Test
    fun `create should use customerId as aggregateId`() {
        // Given
        val customerId = UUID.randomUUID()

        // When
        val event = CustomerActivated.create(
            customerId = customerId,
            activatedAt = Instant.now(),
            emailVerified = true,
            correlationId = UUID.randomUUID(),
            causationId = UUID.randomUUID()
        )

        // Then
        assertEquals(customerId, event.aggregateId)
    }
}
