package com.acme.identity.infrastructure.messaging

import com.acme.identity.domain.events.UserRegistered
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

@Component
class UserEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(UserEventPublisher::class.java)

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
