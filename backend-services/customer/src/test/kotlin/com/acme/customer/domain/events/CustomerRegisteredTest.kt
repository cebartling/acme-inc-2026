package com.acme.customer.domain.events

import com.acme.customer.domain.CustomerStatus
import com.acme.customer.domain.CustomerType
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CustomerRegisteredTest {

    @Test
    fun `create should generate event with all required fields`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val customerNumber = "ACME-202601-000001"
        val email = "test@example.com"
        val firstName = "Jane"
        val lastName = "Doe"
        val status = CustomerStatus.PENDING_VERIFICATION
        val type = CustomerType.INDIVIDUAL
        val registeredAt = Instant.now()
        val correlationId = UUID.randomUUID()
        val causationId = UUID.randomUUID()

        // When
        val event = CustomerRegistered.create(
            customerId = customerId,
            userId = userId,
            customerNumber = customerNumber,
            email = email,
            firstName = firstName,
            lastName = lastName,
            status = status,
            type = type,
            registeredAt = registeredAt,
            correlationId = correlationId,
            causationId = causationId
        )

        // Then
        assertNotNull(event.eventId)
        assertEquals(CustomerRegistered.EVENT_TYPE, event.eventType)
        assertEquals(CustomerRegistered.EVENT_VERSION, event.eventVersion)
        assertNotNull(event.timestamp)
        assertEquals(customerId, event.aggregateId)
        assertEquals(CustomerRegistered.AGGREGATE_TYPE, event.aggregateType)
        assertEquals(correlationId, event.correlationId)
        assertEquals(causationId, event.causationId)

        // Verify payload
        assertEquals(customerId, event.payload.customerId)
        assertEquals(userId, event.payload.userId)
        assertEquals(customerNumber, event.payload.customerNumber)
        assertEquals(email, event.payload.email)
        assertEquals(firstName, event.payload.firstName)
        assertEquals(lastName, event.payload.lastName)
        assertEquals(status, event.payload.status)
        assertEquals(type, event.payload.type)
        assertEquals(registeredAt, event.payload.registeredAt)
    }

    @Test
    fun `create should generate unique event ID each time`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val causationId = UUID.randomUUID()
        val registeredAt = Instant.now()

        // When
        val event1 = CustomerRegistered.create(
            customerId = customerId,
            userId = userId,
            customerNumber = "ACME-202601-000001",
            email = "test@example.com",
            firstName = "Jane",
            lastName = "Doe",
            status = CustomerStatus.PENDING_VERIFICATION,
            type = CustomerType.INDIVIDUAL,
            registeredAt = registeredAt,
            correlationId = correlationId,
            causationId = causationId
        )
        val event2 = CustomerRegistered.create(
            customerId = customerId,
            userId = userId,
            customerNumber = "ACME-202601-000001",
            email = "test@example.com",
            firstName = "Jane",
            lastName = "Doe",
            status = CustomerStatus.PENDING_VERIFICATION,
            type = CustomerType.INDIVIDUAL,
            registeredAt = registeredAt,
            correlationId = correlationId,
            causationId = causationId
        )

        // Then
        assertNotEquals(event1.eventId, event2.eventId)
    }

    @Test
    fun `EVENT_TYPE constant should be CustomerRegistered`() {
        assertEquals("CustomerRegistered", CustomerRegistered.EVENT_TYPE)
    }

    @Test
    fun `EVENT_VERSION constant should be 1_0`() {
        assertEquals("1.0", CustomerRegistered.EVENT_VERSION)
    }

    @Test
    fun `AGGREGATE_TYPE constant should be Customer`() {
        assertEquals("Customer", CustomerRegistered.AGGREGATE_TYPE)
    }

    @Test
    fun `TOPIC constant should be customer_events`() {
        assertEquals("customer.events", CustomerRegistered.TOPIC)
    }

    private fun assertNotEquals(a: UUID, b: UUID) {
        if (a == b) {
            throw AssertionError("Expected $a to not equal $b")
        }
    }
}
