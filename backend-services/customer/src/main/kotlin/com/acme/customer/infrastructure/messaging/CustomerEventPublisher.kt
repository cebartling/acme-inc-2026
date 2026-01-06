package com.acme.customer.infrastructure.messaging

import com.acme.customer.domain.events.CustomerRegistered
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
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
    private val objectMapper: ObjectMapper,
    @Value("\${customer.events.publish.timeout-seconds:10}")
    private val publishTimeoutSeconds: Long
) {
    private val logger = LoggerFactory.getLogger(CustomerEventPublisher::class.java)

    /**
     * Publishes a CustomerRegistered event to Kafka.
     *
     * The event is serialized to JSON and published synchronously within
     * the transaction to ensure that failures prevent transaction commit.
     * The aggregate ID (customer ID) is used as the message key to
     * ensure ordering of events for the same customer.
     *
     * @param event The CustomerRegistered event to publish.
     * @throws RuntimeException if publishing fails, causing transaction rollback.
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
            val sendResult = kafkaTemplate.send(CustomerRegistered.TOPIC, key, value)
                .get(publishTimeoutSeconds, TimeUnit.SECONDS)
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
