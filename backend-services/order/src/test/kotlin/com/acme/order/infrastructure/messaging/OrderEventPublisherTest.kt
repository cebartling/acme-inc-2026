package com.acme.order.infrastructure.messaging

import com.acme.order.config.JacksonConfig
import com.acme.order.domain.events.DomainEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

class OrderEventPublisherTest {

    private class TestEvent(aggregateId: UUID, correlationId: UUID) : DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "TestEvent",
        eventVersion = "1.0",
        timestamp = Instant.parse("2026-10-18T12:00:00Z"),
        aggregateId = aggregateId,
        aggregateType = "Order",
        correlationId = correlationId
    )

    private val kafkaTemplate = mockk<KafkaTemplate<String, String>>()
    private val objectMapper = JacksonConfig().objectMapper()
    private val publisher = OrderEventPublisher(kafkaTemplate, objectMapper, publishTimeoutSeconds = 1)

    @Test
    fun `publishes the event envelope to order events keyed by aggregate id`() {
        val event = TestEvent(aggregateId = UUID.randomUUID(), correlationId = UUID.randomUUID())
        val value = slot<String>()
        every { kafkaTemplate.send(ORDER_EVENTS_TOPIC, any(), capture(value)) } answers {
            val record = ProducerRecord(ORDER_EVENTS_TOPIC, secondArg<String>(), thirdArg<String>())
            val metadata = RecordMetadata(TopicPartition(ORDER_EVENTS_TOPIC, 0), 0, 0, 0, 0, 0)
            CompletableFuture.completedFuture(SendResult(record, metadata))
        }

        publisher.publish(event)

        verify { kafkaTemplate.send(ORDER_EVENTS_TOPIC, event.aggregateId.toString(), any()) }
        val json = objectMapper.readTree(value.captured)
        assertEquals(event.eventId.toString(), json["eventId"].asText())
        assertEquals("TestEvent", json["eventType"].asText())
        assertEquals("1.0", json["eventVersion"].asText())
        assertEquals("2026-10-18T12:00:00Z", json["timestamp"].asText())
        assertEquals(event.aggregateId.toString(), json["aggregateId"].asText())
        assertEquals("Order", json["aggregateType"].asText())
        assertEquals(event.correlationId.toString(), json["correlationId"].asText())
    }

    @Test
    fun `a failed send is thrown to the caller`() {
        val event = TestEvent(aggregateId = UUID.randomUUID(), correlationId = UUID.randomUUID())
        every { kafkaTemplate.send(ORDER_EVENTS_TOPIC, any(), any()) } returns
            CompletableFuture.failedFuture(IllegalStateException("broker rejected"))

        val thrown = assertThrows(ExecutionException::class.java) { publisher.publish(event) }
        assertEquals("broker rejected", thrown.cause?.message)
    }

    @Test
    fun `a send that outlasts the publish timeout is thrown to the caller`() {
        val event = TestEvent(aggregateId = UUID.randomUUID(), correlationId = UUID.randomUUID())
        every { kafkaTemplate.send(ORDER_EVENTS_TOPIC, any(), any()) } returns CompletableFuture()

        assertThrows(TimeoutException::class.java) { publisher.publish(event) }
    }
}
