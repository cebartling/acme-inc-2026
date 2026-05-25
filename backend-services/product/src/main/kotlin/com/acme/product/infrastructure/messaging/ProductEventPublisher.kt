package com.acme.product.infrastructure.messaging

import com.acme.product.domain.events.SearchExecuted
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Kafka publisher for product domain events.
 *
 * Publishes SearchExecuted events to the product.events topic
 * for consumption by downstream analytics services.
 */
@Component
class ProductEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    @Value("\${product.events.publish.timeout-seconds:10}")
    private val publishTimeoutSeconds: Long
) {
    private val logger = LoggerFactory.getLogger(ProductEventPublisher::class.java)

    /**
     * Publishes a [SearchExecuted] event to Kafka.
     *
     * The aggregate ID is used as the message key.
     *
     * @param event The event to publish.
     * @throws RuntimeException if publishing fails.
     */
    fun publish(event: SearchExecuted) {
        val key = event.aggregateId.toString()
        val value = objectMapper.writeValueAsString(event)

        logger.debug(
            "Publishing {} event {} to topic {}",
            event.eventType,
            event.eventId,
            SearchExecuted.TOPIC
        )

        try {
            val sendResult = kafkaTemplate.send(SearchExecuted.TOPIC, key, value)
                .get(publishTimeoutSeconds, TimeUnit.SECONDS)
            logger.info(
                "Published {} event {} to topic {} partition {} offset {}",
                event.eventType,
                event.eventId,
                sendResult.recordMetadata.topic(),
                sendResult.recordMetadata.partition(),
                sendResult.recordMetadata.offset()
            )
        } catch (ex: Exception) {
            logger.error(
                "Failed to publish {} event {}: {}",
                event.eventType,
                event.eventId,
                ex.message,
                ex
            )
            throw RuntimeException("Failed to publish event to Kafka", ex)
        }
    }
}
