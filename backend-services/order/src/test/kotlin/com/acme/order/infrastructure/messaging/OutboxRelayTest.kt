package com.acme.order.infrastructure.messaging

import com.acme.order.infrastructure.persistence.OutboxMessage
import com.acme.order.infrastructure.persistence.OutboxRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.util.UUID
import java.util.concurrent.CompletableFuture

class OutboxRelayTest {

    private val outboxRepository = mockk<OutboxRepository>()
    private val kafkaTemplate = mockk<KafkaTemplate<String, String>>()
    private val relay = OutboxRelay(
        outboxRepository,
        kafkaTemplate,
        publishTimeoutSeconds = 1,
        maxAttempts = 3,
        batchSize = 10
    )

    private fun message(retryCount: Int = 0) = OutboxMessage(
        aggregateId = UUID.randomUUID(),
        aggregateType = "Order",
        eventType = "TestEvent",
        eventId = UUID.randomUUID(),
        topic = ORDER_EVENTS_TOPIC,
        messageKey = UUID.randomUUID().toString(),
        payload = """{"eventType":"TestEvent"}""",
        retryCount = retryCount
    )

    private fun sent(message: OutboxMessage): CompletableFuture<SendResult<String, String>> {
        val record = ProducerRecord(message.topic, message.messageKey, message.payload)
        val metadata = RecordMetadata(TopicPartition(message.topic, 0), 0, 0, 0, 0, 0)
        return CompletableFuture.completedFuture(SendResult(record, metadata))
    }

    init {
        every { outboxRepository.save(any<OutboxMessage>()) } answers { firstArg() }
    }

    @Test
    fun `sends each message with its topic, key and payload and marks it sent`() {
        val first = message()
        val second = message()
        every { outboxRepository.findSendable(3, PageRequest.of(0, 10)) } returns listOf(first, second)
        every { kafkaTemplate.send(ORDER_EVENTS_TOPIC, first.messageKey, first.payload) } returns sent(first)
        every { kafkaTemplate.send(ORDER_EVENTS_TOPIC, second.messageKey, second.payload) } returns sent(second)

        relay.relayMessages()

        assertNotNull(first.publishedAt)
        assertNotNull(second.publishedAt)
        verify { outboxRepository.save(first) }
        verify { outboxRepository.save(second) }
    }

    @Test
    fun `a failed send records the attempt and stops the poll`() {
        val failing = message()
        val later = message()
        every { outboxRepository.findSendable(any(), any()) } returns listOf(failing, later)
        every { kafkaTemplate.send(ORDER_EVENTS_TOPIC, failing.messageKey, any()) } returns
            CompletableFuture.failedFuture(IllegalStateException("broker rejected"))

        relay.relayMessages()

        assertNull(failing.publishedAt)
        assertEquals(1, failing.retryCount)
        assertEquals("IllegalStateException: broker rejected", failing.lastError)
        verify { outboxRepository.save(failing) }
        verify(exactly = 0) { kafkaTemplate.send(ORDER_EVENTS_TOPIC, later.messageKey, any()) }
    }

    @Test
    fun `a send that outlasts the publish timeout counts as a failed attempt`() {
        val slow = message(retryCount = 2)
        every { outboxRepository.findSendable(any(), any()) } returns listOf(slow)
        every { kafkaTemplate.send(ORDER_EVENTS_TOPIC, any(), any()) } returns CompletableFuture()

        relay.relayMessages()

        assertNull(slow.publishedAt)
        assertEquals(3, slow.retryCount)
        assertTrue(slow.lastError!!.startsWith("TimeoutException"))
    }

    @Test
    fun `an interrupted send isn't counted as an attempt and leaves the thread interrupted`() {
        val interrupted = message()
        every { outboxRepository.findSendable(any(), any()) } returns listOf(interrupted, message())
        every { kafkaTemplate.send(ORDER_EVENTS_TOPIC, any(), any()) } returns CompletableFuture()

        Thread.currentThread().interrupt()
        relay.relayMessages()

        assertTrue(Thread.interrupted())
        assertEquals(0, interrupted.retryCount)
        verify(exactly = 1) { kafkaTemplate.send(ORDER_EVENTS_TOPIC, any(), any()) }
        verify(exactly = 0) { outboxRepository.save(any<OutboxMessage>()) }
    }

    @Test
    fun `a failed save after a successful send is thrown, not counted as a failed send`() {
        val sentButUnmarked = message()
        every { outboxRepository.findSendable(any(), any()) } returns listOf(sentButUnmarked)
        every { kafkaTemplate.send(ORDER_EVENTS_TOPIC, any(), any()) } returns sent(sentButUnmarked)
        every { outboxRepository.save(any<OutboxMessage>()) } throws IllegalStateException("database down")

        assertThrows(IllegalStateException::class.java) { relay.relayMessages() }

        assertEquals(0, sentButUnmarked.retryCount)
        assertNull(sentButUnmarked.lastError)
        verify(exactly = 1) { outboxRepository.save(any<OutboxMessage>()) }
    }

    @Test
    fun `an attempt limit or batch size below one fails at startup`() {
        assertThrows(IllegalArgumentException::class.java) {
            OutboxRelay(outboxRepository, kafkaTemplate, publishTimeoutSeconds = 1, maxAttempts = 0, batchSize = 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OutboxRelay(outboxRepository, kafkaTemplate, publishTimeoutSeconds = 1, maxAttempts = 3, batchSize = 0)
        }
    }
}
