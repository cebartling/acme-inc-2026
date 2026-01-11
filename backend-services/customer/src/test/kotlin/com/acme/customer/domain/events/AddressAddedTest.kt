package com.acme.customer.domain.events

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AddressAddedTest {

    @Test
    fun `create should generate event with all required fields`() {
        // Given
        val customerId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val type = "SHIPPING"
        val label = "Home"
        val isDefault = true
        val isValidated = false
        val correlationId = UUID.randomUUID()
        val causationId = UUID.randomUUID()

        // When
        val event = AddressAdded.create(
            customerId = customerId,
            addressId = addressId,
            type = type,
            label = label,
            isDefault = isDefault,
            isValidated = isValidated,
            correlationId = correlationId,
            causationId = causationId
        )

        // Then
        assertNotNull(event.eventId)
        assertEquals(AddressAdded.EVENT_TYPE, event.eventType)
        assertEquals(AddressAdded.EVENT_VERSION, event.eventVersion)
        assertNotNull(event.timestamp)
        assertEquals(customerId, event.aggregateId)
        assertEquals(AddressAdded.AGGREGATE_TYPE, event.aggregateType)
        assertEquals(correlationId, event.correlationId)
        assertEquals(causationId, event.causationId)

        // Verify payload
        assertEquals(customerId, event.payload.customerId)
        assertEquals(addressId, event.payload.addressId)
        assertEquals(type, event.payload.type)
        assertEquals(label, event.payload.label)
        assertEquals(isDefault, event.payload.isDefault)
        assertEquals(isValidated, event.payload.isValidated)
    }

    @Test
    fun `create should handle null label`() {
        // Given
        val customerId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val causationId = UUID.randomUUID()

        // When
        val event = AddressAdded.create(
            customerId = customerId,
            addressId = addressId,
            type = "BILLING",
            label = null,
            isDefault = false,
            isValidated = false,
            correlationId = correlationId,
            causationId = causationId
        )

        // Then
        assertNull(event.payload.label)
    }

    @Test
    fun `create should generate unique event ID each time`() {
        // Given
        val customerId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val causationId = UUID.randomUUID()

        // When
        val event1 = AddressAdded.create(
            customerId = customerId,
            addressId = addressId,
            type = "SHIPPING",
            label = "Home",
            isDefault = true,
            isValidated = false,
            correlationId = correlationId,
            causationId = causationId
        )
        val event2 = AddressAdded.create(
            customerId = customerId,
            addressId = addressId,
            type = "SHIPPING",
            label = "Home",
            isDefault = true,
            isValidated = false,
            correlationId = correlationId,
            causationId = causationId
        )

        // Then
        assertNotEquals(event1.eventId, event2.eventId)
    }

    @Test
    fun `EVENT_TYPE constant should be AddressAdded`() {
        assertEquals("AddressAdded", AddressAdded.EVENT_TYPE)
    }

    @Test
    fun `EVENT_VERSION constant should be 1_0`() {
        assertEquals("1.0", AddressAdded.EVENT_VERSION)
    }

    @Test
    fun `AGGREGATE_TYPE constant should be Customer`() {
        assertEquals("Customer", AddressAdded.AGGREGATE_TYPE)
    }

    @Test
    fun `TOPIC constant should be customer_events`() {
        assertEquals("customer.events", AddressAdded.TOPIC)
    }
}
