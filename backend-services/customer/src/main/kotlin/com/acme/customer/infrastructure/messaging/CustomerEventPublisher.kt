package com.acme.customer.infrastructure.messaging

import com.acme.customer.domain.events.CustomerRegistered
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Kafka publisher for customer domain events.
 *
 * Publishes CustomerRegistered events to the customer.events topic
 * for consumption by downstream services.
 */
@Component
class CustomerEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(CustomerEventPublisher::class.java)

    /**
     * Publishes a CustomerRegistered event to Kafka synchronously.
     *
     * The event is serialized to JSON and published to Kafka. This method
     * blocks until the Kafka broker acknowledges receipt of the message.
     * The aggregate ID (customer ID) is used as the message key to
     * ensure ordering of events for the same customer.
     *
     * Exceptions are propagated back to the caller so that the
     * main transaction can be rolled back if publishing fails.
     *
     * @param event The CustomerRegistered event to publish.
     * @throws RuntimeException if publishing fails.
     */
    fun publish(event: CustomerRegistered) {
        val key = event.aggregateId.toString()
        val value = objectMapper.writeValueAsString(event)

        logger.debug(
            "Publishing {} event {} for customer {} to topic {}",
            event.eventType,
            event.eventId,
            event.payload.customerId,
            CustomerRegistered.TOPIC
        )

        try {
            val sendResult = kafkaTemplate.send(CustomerRegistered.TOPIC, key, value).get(30, TimeUnit.SECONDS)
            logger.info(
                "Published {} event {} for customer {} to topic {} partition {} offset {}",
                event.eventType,
                event.eventId,
                event.payload.customerId,
                sendResult.recordMetadata.topic(),
                sendResult.recordMetadata.partition(),
                sendResult.recordMetadata.offset()
            )
        } catch (ex: Exception) {
            logger.error(
                "Failed to publish {} event {} for customer {}: {}",
                event.eventType,
                event.eventId,
                event.payload.customerId,
                ex.message,
                ex
            )
            throw RuntimeException("Failed to publish event to Kafka", ex)
        }
    }
}
