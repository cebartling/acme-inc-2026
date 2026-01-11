package com.acme.customer.domain.events

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AddressRemovedTest {

    @Test
    fun `create should generate event with all required fields`() {
        // Given
        val customerId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val type = "SHIPPING"
        val wasDefault = true
        val correlationId = UUID.randomUUID()
        val causationId = UUID.randomUUID()

        // When
        val event = AddressRemoved.create(
            customerId = customerId,
            addressId = addressId,
            type = type,
            wasDefault = wasDefault,
            correlationId = correlationId,
            causationId = causationId
        )

        // Then
        assertNotNull(event.eventId)
        assertEquals(AddressRemoved.EVENT_TYPE, event.eventType)
        assertEquals(AddressRemoved.EVENT_VERSION, event.eventVersion)
        assertNotNull(event.timestamp)
        assertEquals(customerId, event.aggregateId)
        assertEquals(AddressRemoved.AGGREGATE_TYPE, event.aggregateType)
        assertEquals(correlationId, event.correlationId)
        assertEquals(causationId, event.causationId)

        // Verify payload
        assertEquals(customerId, event.payload.customerId)
        assertEquals(addressId, event.payload.addressId)
        assertEquals(type, event.payload.type)
        assertTrue(event.payload.wasDefault)
    }

    @Test
    fun `create should handle wasDefault false`() {
        // Given
        val customerId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val causationId = UUID.randomUUID()

        // When
        val event = AddressRemoved.create(
            customerId = customerId,
            addressId = addressId,
            type = "BILLING",
            wasDefault = false,
            correlationId = correlationId,
            causationId = causationId
        )

        // Then
        assertFalse(event.payload.wasDefault)
    }

    @Test
    fun `create should generate unique event ID each time`() {
        // Given
        val customerId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val causationId = UUID.randomUUID()

        // When
        val event1 = AddressRemoved.create(
            customerId = customerId,
            addressId = addressId,
            type = "SHIPPING",
            wasDefault = true,
            correlationId = correlationId,
            causationId = causationId
        )
        val event2 = AddressRemoved.create(
            customerId = customerId,
            addressId = addressId,
            type = "SHIPPING",
            wasDefault = true,
            correlationId = correlationId,
            causationId = causationId
        )

        // Then
        assertNotEquals(event1.eventId, event2.eventId)
    }

    @Test
    fun `EVENT_TYPE constant should be AddressRemoved`() {
        assertEquals("AddressRemoved", AddressRemoved.EVENT_TYPE)
    }

    @Test
    fun `EVENT_VERSION constant should be 1_0`() {
        assertEquals("1.0", AddressRemoved.EVENT_VERSION)
    }

    @Test
    fun `AGGREGATE_TYPE constant should be Customer`() {
        assertEquals("Customer", AddressRemoved.AGGREGATE_TYPE)
    }

    @Test
    fun `TOPIC constant should be customer_events`() {
        assertEquals("customer.events", AddressRemoved.TOPIC)
    }
}
