package com.acme.cart.infrastructure.messaging

import com.acme.cart.config.JacksonConfig
import com.acme.cart.domain.events.DomainEvent
import com.acme.cart.infrastructure.persistence.OutboxMessage
import com.acme.cart.infrastructure.persistence.OutboxRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class OutboxWriterTest {

    private class TestEvent(aggregateId: UUID, correlationId: UUID) : DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "TestEvent",
        eventVersion = "1.0",
        timestamp = Instant.parse("2026-10-18T12:00:00Z"),
        aggregateId = aggregateId,
        aggregateType = "Cart",
        correlationId = correlationId
    )

    private val outboxRepository = mockk<OutboxRepository>()
    private val objectMapper = JacksonConfig().objectMapper()
    private val writer = OutboxWriter(outboxRepository, objectMapper)

    @Test
    fun `writes the event envelope for cart events keyed by aggregate id`() {
        val event = TestEvent(aggregateId = UUID.randomUUID(), correlationId = UUID.randomUUID())
        val saved = slot<OutboxMessage>()
        every { outboxRepository.save(capture(saved)) } answers { firstArg() }

        writer.write(event)

        val message = saved.captured
        assertEquals(CART_EVENTS_TOPIC, message.topic)
        assertEquals(event.aggregateId.toString(), message.messageKey)
        assertEquals(event.aggregateId, message.aggregateId)
        assertEquals("Cart", message.aggregateType)
        assertEquals("TestEvent", message.eventType)
        assertEquals(event.eventId, message.eventId)
        assertNull(message.publishedAt)
        assertEquals(0, message.retryCount)
        val json = objectMapper.readTree(message.payload)
        assertEquals(event.eventId.toString(), json["eventId"].asText())
        assertEquals("TestEvent", json["eventType"].asText())
        assertEquals("1.0", json["eventVersion"].asText())
        assertEquals("2026-10-18T12:00:00Z", json["timestamp"].asText())
        assertEquals(event.aggregateId.toString(), json["aggregateId"].asText())
        assertEquals("Cart", json["aggregateType"].asText())
        assertEquals(event.correlationId.toString(), json["correlationId"].asText())
    }
}
