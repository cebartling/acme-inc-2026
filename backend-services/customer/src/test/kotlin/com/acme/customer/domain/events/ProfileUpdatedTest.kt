package com.acme.customer.domain.events

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProfileUpdatedTest {

    @Test
    fun `create should generate event with correct fields`() {
        // Given
        val customerId = UUID.randomUUID()
        val changedFields = listOf("phone", "dateOfBirth")
        val profileCompleteness = 65
        val correlationId = UUID.randomUUID()
        val causationId = UUID.randomUUID()

        // When
        val event = ProfileUpdated.create(
            customerId = customerId,
            changedFields = changedFields,
            profileCompleteness = profileCompleteness,
            correlationId = correlationId,
            causationId = causationId
        )

        // Then
        assertNotNull(event.eventId)
        assertNotNull(event.timestamp)
        assertEquals("ProfileUpdated", event.eventType)
        assertEquals("1.0", event.eventVersion)
        assertEquals("Customer", event.aggregateType)
        assertEquals(customerId, event.aggregateId)
        assertEquals(correlationId, event.correlationId)
        assertEquals(causationId, event.causationId)

        // Payload assertions
        assertEquals(customerId, event.payload.customerId)
        assertEquals(changedFields, event.payload.changedFields)
        assertEquals(profileCompleteness, event.payload.profileCompleteness)
    }

    @Test
    fun `create should generate unique event IDs`() {
        // Given
        val customerId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val causationId = UUID.randomUUID()

        // When
        val event1 = ProfileUpdated.create(
            customerId = customerId,
            changedFields = listOf("phone"),
            profileCompleteness = 40,
            correlationId = correlationId,
            causationId = causationId
        )

        val event2 = ProfileUpdated.create(
            customerId = customerId,
            changedFields = listOf("phone"),
            profileCompleteness = 40,
            correlationId = correlationId,
            causationId = causationId
        )

        // Then
        assertTrue(event1.eventId != event2.eventId)
    }

    @Test
    fun `TOPIC constant should be customer events`() {
        // Then
        assertEquals("customer.events", ProfileUpdated.TOPIC)
    }

    @Test
    fun `EVENT_TYPE constant should be ProfileUpdated`() {
        // Then
        assertEquals("ProfileUpdated", ProfileUpdated.EVENT_TYPE)
    }

    @Test
    fun `AGGREGATE_TYPE constant should be Customer`() {
        // Then
        assertEquals("Customer", ProfileUpdated.AGGREGATE_TYPE)
    }

    @Test
    fun `create should handle empty changed fields list`() {
        // Given
        val customerId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val causationId = UUID.randomUUID()

        // When
        val event = ProfileUpdated.create(
            customerId = customerId,
            changedFields = emptyList(),
            profileCompleteness = 25,
            correlationId = correlationId,
            causationId = causationId
        )

        // Then
        assertTrue(event.payload.changedFields.isEmpty())
    }
}
