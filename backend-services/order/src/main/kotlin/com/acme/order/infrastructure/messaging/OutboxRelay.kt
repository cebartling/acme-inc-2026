package com.acme.order.infrastructure.messaging

import com.acme.order.infrastructure.persistence.OutboxMessage
import com.acme.order.infrastructure.persistence.OutboxRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.NestedExceptionUtils
import org.springframework.data.domain.PageRequest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Sends the outbox's unsent messages to Kafka (PIN-368, ADR-0022), oldest first.
 *
 * Delivery is at least once: a message sent but not yet marked is sent again on the next
 * poll, so consumers deduplicate by `eventId` (ADR-0018). A failed send records the attempt
 * and ends the poll, so a later event for the same order isn't sent ahead of it while the
 * message has attempts left; once it has used them up, later events are sent without it.
 * Each `save` commits on its own; there is no transaction around the Kafka send.
 */
@Component
class OutboxRelay(
    private val outboxRepository: OutboxRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Value("\${acme.order.events.publish-timeout-seconds:10}")
    private val publishTimeoutSeconds: Long,
    @Value("\${acme.order.outbox.max-attempts:5}")
    private val maxAttempts: Int,
    @Value("\${acme.order.outbox.batch-size:100}")
    private val batchSize: Int
) {
    private val logger = LoggerFactory.getLogger(OutboxRelay::class.java)

    init {
        require(maxAttempts >= 1) { "acme.order.outbox.max-attempts must be at least 1, was $maxAttempts" }
        require(batchSize >= 1) { "acme.order.outbox.batch-size must be at least 1, was $batchSize" }
    }

    @Scheduled(fixedDelayString = "\${acme.order.outbox.poll-delay-ms:1000}")
    fun relayMessages() {
        val messages = outboxRepository.findSendable(maxAttempts, PageRequest.of(0, batchSize))
        for (message in messages) {
            if (!send(message)) return
        }
    }

    /**
     * Sends one message and marks it sent. Only the Kafka send counts as an attempt: an
     * interrupt (shutdown) doesn't, and a failed `save` afterwards is thrown to the scheduler,
     * so the message is sent again on the next poll.
     */
    private fun send(message: OutboxMessage): Boolean {
        val sendResult = try {
            kafkaTemplate.send(message.topic, message.messageKey, message.payload)
                .get(publishTimeoutSeconds, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return false
        } catch (e: Exception) {
            recordFailedAttempt(message, e)
            return false
        }
        message.publishedAt = Instant.now()
        message.lastError = null
        outboxRepository.save(message)
        logger.info(
            "Sent outbox message {} ({} event {}) to topic {} partition {} offset {}",
            message.id,
            message.eventType,
            message.eventId,
            sendResult.recordMetadata.topic(),
            sendResult.recordMetadata.partition(),
            sendResult.recordMetadata.offset()
        )
        return true
    }

    private fun recordFailedAttempt(message: OutboxMessage, e: Exception) {
        val cause = NestedExceptionUtils.getMostSpecificCause(e)
        message.retryCount++
        message.lastError = "${cause.javaClass.simpleName}: ${cause.message}"
        outboxRepository.save(message)
        if (message.retryCount >= maxAttempts) {
            logger.error(
                "Gave up on outbox message {} ({} event {}) after {} attempts; it needs a person. Last error: {}",
                message.id, message.eventType, message.eventId, message.retryCount, message.lastError, e
            )
        } else {
            logger.warn(
                "Failed to send outbox message {} ({} event {}), attempt {}/{}: {}",
                message.id, message.eventType, message.eventId, message.retryCount, maxAttempts, message.lastError
            )
        }
    }
}
