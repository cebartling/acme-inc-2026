package com.acme.identity.infrastructure.messaging

import com.acme.identity.domain.events.UserRegistered
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

/**
 * Kafka publisher for user-related domain events.
 *
 * Publishes events to the `identity.user.events` topic for consumption
 * by downstream services such as notifications and customer management.
 *
 * Events are serialized as JSON. The aggregate ID is used as the message
 * key to ensure all events for a user are processed in order within
 * the same partition.
 *
 * @property kafkaTemplate Spring Kafka template for message publishing.
 * @property objectMapper Jackson mapper for JSON serialization.
 */
@Component
class UserEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(UserEventPublisher::class.java)

    /**
     * Publishes a [UserRegistered] event to Kafka.
     *
     * The publish operation is asynchronous. The returned future completes
     * when Kafka acknowledges receipt of the message. Failures are logged
     * but not rethrown to avoid blocking the registration response.
     *
     * @param event The user registration event to publish.
     * @return A [CompletableFuture] that completes when publishing succeeds.
     */
    fun publish(event: UserRegistered): CompletableFuture<Void> {
        val key = event.aggregateId.toString()
        val value = objectMapper.writeValueAsString(event)

        logger.debug("Publishing UserRegistered event for user: {}", event.payload.userId)

        return kafkaTemplate.send(UserRegistered.TOPIC, key, value)
            .thenAccept { result ->
                logger.info(
                    "Published UserRegistered event for user {} to topic {} partition {} offset {}",
                    event.payload.userId,
                    result.recordMetadata.topic(),
                    result.recordMetadata.partition(),
                    result.recordMetadata.offset()
                )
            }
            .exceptionally { ex ->
                logger.error("Failed to publish UserRegistered event for user {}", event.payload.userId, ex)
                throw RuntimeException("Failed to publish event", ex)
            }
    }
}
