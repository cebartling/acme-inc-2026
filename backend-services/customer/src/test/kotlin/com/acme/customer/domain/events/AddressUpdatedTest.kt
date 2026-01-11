package com.acme.customer.domain.events

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AddressUpdatedTest {

    @Test
    fun `create should generate event with all required fields`() {
        // Given
        val customerId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val changedFields = listOf("city", "state", "postalCode")
        val isDefault = true
        val isValidated = false
        val correlationId = UUID.randomUUID()
        val causationId = UUID.randomUUID()

        // When
        val event = AddressUpdated.create(
            customerId = customerId,
            addressId = addressId,
            changedFields = changedFields,
            isDefault = isDefault,
            isValidated = isValidated,
            correlationId = correlationId,
            causationId = causationId
        )

        // Then
        assertNotNull(event.eventId)
        assertEquals(AddressUpdated.EVENT_TYPE, event.eventType)
        assertEquals(AddressUpdated.EVENT_VERSION, event.eventVersion)
        assertNotNull(event.timestamp)
        assertEquals(customerId, event.aggregateId)
        assertEquals(AddressUpdated.AGGREGATE_TYPE, event.aggregateType)
        assertEquals(correlationId, event.correlationId)
        assertEquals(causationId, event.causationId)

        // Verify payload
        assertEquals(customerId, event.payload.customerId)
        assertEquals(addressId, event.payload.addressId)
        assertEquals(changedFields, event.payload.changedFields)
        assertEquals(isDefault, event.payload.isDefault)
        assertEquals(isValidated, event.payload.isValidated)
    }

    @Test
    fun `create should handle empty changedFields list`() {
        // Given
        val customerId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val causationId = UUID.randomUUID()

        // When
        val event = AddressUpdated.create(
            customerId = customerId,
            addressId = addressId,
            changedFields = emptyList(),
            isDefault = false,
            isValidated = false,
            correlationId = correlationId,
            causationId = causationId
        )

        // Then
        assertTrue(event.payload.changedFields.isEmpty())
    }

    @Test
    fun `create should generate unique event ID each time`() {
        // Given
        val customerId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val causationId = UUID.randomUUID()
        val changedFields = listOf("city")

        // When
        val event1 = AddressUpdated.create(
            customerId = customerId,
            addressId = addressId,
            changedFields = changedFields,
            isDefault = true,
            isValidated = false,
            correlationId = correlationId,
            causationId = causationId
        )
        val event2 = AddressUpdated.create(
            customerId = customerId,
            addressId = addressId,
            changedFields = changedFields,
            isDefault = true,
            isValidated = false,
            correlationId = correlationId,
            causationId = causationId
        )

        // Then
        assertNotEquals(event1.eventId, event2.eventId)
    }

    @Test
    fun `EVENT_TYPE constant should be AddressUpdated`() {
        assertEquals("AddressUpdated", AddressUpdated.EVENT_TYPE)
    }

    @Test
    fun `EVENT_VERSION constant should be 1_0`() {
        assertEquals("1.0", AddressUpdated.EVENT_VERSION)
    }

    @Test
    fun `AGGREGATE_TYPE constant should be Customer`() {
        assertEquals("Customer", AddressUpdated.AGGREGATE_TYPE)
    }

    @Test
    fun `TOPIC constant should be customer_events`() {
        assertEquals("customer.events", AddressUpdated.TOPIC)
    }
}
