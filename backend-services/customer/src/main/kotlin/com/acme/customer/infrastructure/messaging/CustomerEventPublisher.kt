package com.acme.customer.infrastructure.messaging

import com.acme.customer.domain.events.CustomerRegistered
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

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
        } catch (ex: TimeoutException) {
            logger.error(
                "Timeout publishing {} event {} for customer {} to Kafka after 30 seconds",
                event.eventType,
                event.eventId,
                event.payload.customerId,
                ex
            )
            throw RuntimeException("Kafka publishing timeout after 30 seconds", ex)
        } catch (ex: ExecutionException) {
            val errorMessage = ex.cause?.message ?: ex.message
            val errorCause = ex.cause ?: ex
            logger.error(
                "Failed to publish {} event {} for customer {}: {}",
                event.eventType,
                event.eventId,
                event.payload.customerId,
                errorMessage,
                errorCause
            )
            throw RuntimeException("Failed to publish event to Kafka: $errorMessage", errorCause)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.error(
                "Interrupted while publishing {} event {} for customer {}",
                event.eventType,
                event.eventId,
                event.payload.customerId,
                ex
            )
            throw RuntimeException("Kafka publishing interrupted", ex)
        }
    }
}
