package com.acme.notification.infrastructure.messaging

import com.acme.notification.domain.events.NotificationSent
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

/**
 * Publishes notification events to Kafka.
 */
@Component
class NotificationEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    @Value("\${notification.events.output-topic}")
    private val outputTopic: String
) {
    private val logger = LoggerFactory.getLogger(NotificationEventPublisher::class.java)

    /**
     * Publishes a NotificationSent event to Kafka.
     *
     * @param event The event to publish.
     */
    fun publish(event: NotificationSent) {
        try {
            val eventJson = objectMapper.writeValueAsString(event)
            val key = event.aggregateId.toString()

            kafkaTemplate.send(outputTopic, key, eventJson)
                .whenComplete { result, ex ->
                    if (ex != null) {
                        logger.error(
                            "Failed to publish {} event {} to topic {}: {}",
                            event.eventType,
                            event.eventId,
                            outputTopic,
                            ex.message,
                            ex
                        )
                    } else {
                        logger.info(
                            "Published {} event {} to topic {} partition {} offset {}",
                            event.eventType,
                            event.eventId,
                            result.recordMetadata.topic(),
                            result.recordMetadata.partition(),
                            result.recordMetadata.offset()
                        )
                    }
                }
        } catch (e: Exception) {
            logger.error(
                "Failed to serialize {} event {}: {}",
                event.eventType,
                event.eventId,
                e.message,
                e
            )
        }
    }
}
