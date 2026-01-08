package com.acme.customer.infrastructure.messaging

import com.acme.customer.infrastructure.persistence.OutboxMessage
import com.acme.customer.infrastructure.persistence.OutboxRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Outbox relay that publishes messages from the outbox to Kafka.
 *
 * This component implements the publish side of the Transactional Outbox pattern.
 * It polls the outbox table for unpublished messages and publishes them to Kafka
 * asynchronously, decoupled from the original database transaction.
 *
 * Key benefits:
 * - Database transaction success is not coupled to Kafka availability
 * - Events are guaranteed to be published eventually (at-least-once delivery)
 * - Kafka publishing failures don't cause domain operation rollbacks
 */
@Component
class OutboxRelay(
    private val outboxRepository: OutboxRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Value("\${customer.outbox.publish.timeout-seconds:10}")
    private val publishTimeoutSeconds: Long,
    @Value("\${customer.outbox.max-retry-count:5}")
    private val maxRetryCount: Int
) {
    private val logger = LoggerFactory.getLogger(OutboxRelay::class.java)

    /**
     * Polls for unpublished messages and publishes them to Kafka.
     *
     * Runs on a fixed delay schedule. For each unpublished message:
     * 1. Attempts to publish to Kafka
     * 2. Marks as published on success
     * 3. Updates retry count and error on failure
     * 4. Skips messages that have exceeded max retry count
     *
     * Each message update runs in its own transaction to ensure
     * progress even if individual messages fail.
     */
    @Scheduled(fixedDelayString = "\${customer.outbox.poll-delay-ms:1000}")
    fun relayMessages() {
        val unpublished = outboxRepository.findUnpublished()
        
        if (unpublished.isEmpty()) {
            return
        }

        logger.debug("Found {} unpublished outbox messages", unpublished.size)

        unpublished.forEach { message ->
            if (message.retryCount >= maxRetryCount) {
                logger.warn(
                    "Skipping outbox message {} - exceeded max retry count {} (last error: {})",
                    message.id,
                    maxRetryCount,
                    message.lastError
                )
                return@forEach
            }

            try {
                publishMessage(message)
            } catch (e: Exception) {
                logger.error(
                    "Failed to process outbox message {}: {}",
                    message.id,
                    e.message,
                    e
                )
            }
        }
    }

    /**
     * Publishes a single message to Kafka and updates its status.
     *
     * Runs in a separate transaction to ensure each message
     * is processed independently.
     */
    @Transactional
    fun publishMessage(message: OutboxMessage) {
        logger.debug(
            "Publishing outbox message {} - event {} to topic {}",
            message.id,
            message.eventId,
            message.topic
        )

        try {
            val sendResult = kafkaTemplate.send(message.topic, message.messageKey, message.payload)
                .get(publishTimeoutSeconds, TimeUnit.SECONDS)

            message.publishedAt = Instant.now()
            message.lastError = null
            outboxRepository.save(message)

            logger.info(
                "Published outbox message {} - {} event {} to topic {} partition {} offset {}",
                message.id,
                message.eventType,
                message.eventId,
                sendResult.recordMetadata.topic(),
                sendResult.recordMetadata.partition(),
                sendResult.recordMetadata.offset()
            )
        } catch (e: Exception) {
            message.retryCount++
            message.lastError = "${e.javaClass.simpleName}: ${e.message}"
            outboxRepository.save(message)

            logger.error(
                "Failed to publish outbox message {} (retry {}/{}): {}",
                message.id,
                message.retryCount,
                maxRetryCount,
                e.message,
                e
            )
            throw e
        }
    }
}
