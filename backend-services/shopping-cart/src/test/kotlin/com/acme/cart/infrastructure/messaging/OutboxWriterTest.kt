package com.acme.cart.infrastructure.messaging

import com.acme.cart.config.JacksonConfig
import com.acme.cart.api.v1.CartResponse
import com.acme.cart.api.v1.CartSummaryResponse
import com.acme.cart.domain.CartStatus
import com.acme.cart.domain.events.DomainEvent
import com.acme.cart.infrastructure.projection.CartSnapshotFactory
import com.acme.cart.infrastructure.projection.CartSnapshotMessage
import com.acme.cart.infrastructure.projection.ReadAfter
import com.acme.cart.infrastructure.persistence.OutboxMessage
import com.acme.cart.infrastructure.persistence.OutboxRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.math.BigDecimal
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
    private val snapshotFactory = mockk<CartSnapshotFactory>()
    private val writer = OutboxWriter(outboxRepository, objectMapper, snapshotFactory)
    private val saved = mutableListOf<OutboxMessage>()

    @AfterEach
    fun clearRequest() = RequestContextHolder.resetRequestAttributes()

    private fun snapshotOf(event: DomainEvent, userId: UUID? = UUID.randomUUID()) = CartSnapshotMessage(
        sourceEventId = event.eventId,
        cartId = event.aggregateId,
        userId = userId,
        sessionId = null,
        cart = CartResponse(
            id = event.aggregateId,
            status = CartStatus.ACTIVE,
            items = emptyList(),
            summary = CartSummaryResponse(itemCount = 0, subtotal = BigDecimal.ZERO, currency = "USD")
        )
    )

    private fun givenOutboxSavesAreCaptured(event: DomainEvent) {
        every { outboxRepository.save(capture(saved)) } answers { firstArg() }
        every { snapshotFactory.build(event.aggregateId, event.eventId) } returns snapshotOf(event)
    }

    @Test
    fun `writes the event envelope for cart events keyed by aggregate id`() {
        val event = TestEvent(aggregateId = UUID.randomUUID(), correlationId = UUID.randomUUID())
        givenOutboxSavesAreCaptured(event)

        writer.write(event)

        val message = saved.first()
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

    @Test
    fun `writes a snapshot row for the read model after the event, on its own topic`() {
        val event = TestEvent(aggregateId = UUID.randomUUID(), correlationId = UUID.randomUUID())
        givenOutboxSavesAreCaptured(event)

        writer.write(event)

        assertEquals(2, saved.size)
        assertEquals(CART_EVENTS_TOPIC, saved[0].topic)
        val snapshot = saved[1]
        assertEquals(CartSnapshotMessage.TOPIC, snapshot.topic)
        assertEquals(CartSnapshotMessage.EVENT_TYPE, snapshot.eventType)
        assertEquals(event.aggregateId.toString(), snapshot.messageKey)
        assertEquals(event.aggregateId, snapshot.aggregateId)
        assertEquals("Cart", snapshot.aggregateType)
        val json = objectMapper.readTree(snapshot.payload)
        assertEquals(event.eventId.toString(), json["sourceEventId"].asText())
        assertEquals(event.aggregateId.toString(), json["cartId"].asText())
        assertEquals(event.aggregateId.toString(), json["cart"]["id"].asText())
    }

    @Test
    fun `a snapshot of a cart that no longer exists carries no cart`() {
        val event = TestEvent(aggregateId = UUID.randomUUID(), correlationId = UUID.randomUUID())
        every { outboxRepository.save(capture(saved)) } answers { firstArg() }
        every { snapshotFactory.build(event.aggregateId, event.eventId) } returns
            CartSnapshotMessage(event.eventId, event.aggregateId, userId = null, sessionId = null, cart = null)

        writer.write(event)

        assertNull(objectMapper.readTree(saved[1].payload)["cart"].takeUnless { it.isNull })
    }

    @Test
    fun `tells the client to read after the event when handling a web request`() {
        val event = TestEvent(aggregateId = UUID.randomUUID(), correlationId = UUID.randomUUID())
        givenOutboxSavesAreCaptured(event)
        val response = MockHttpServletResponse()
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(MockHttpServletRequest(), response))

        writer.write(event)

        assertEquals(event.eventId.toString(), response.getHeader(ReadAfter.HEADER))
    }

    @Test
    fun `writes without a web request set no header and do not fail`() {
        val event = TestEvent(aggregateId = UUID.randomUUID(), correlationId = UUID.randomUUID())
        givenOutboxSavesAreCaptured(event)

        writer.write(event)

        assertEquals(2, saved.size)
    }
}
