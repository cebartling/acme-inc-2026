package com.acme.order.infrastructure.messaging

import com.acme.order.domain.events.DomainEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

internal const val ORDER_EVENTS_TOPIC = "order.events"

@Component
class OrderEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    @Value("\${acme.order.events.publish-timeout-seconds:10}")
    private val publishTimeoutSeconds: Long
) {
    private val logger = LoggerFactory.getLogger(OrderEventPublisher::class.java)

    fun publish(event: DomainEvent) {
        val key = event.aggregateId.toString()
        val value = objectMapper.writeValueAsString(event)

        logger.debug("Publishing {} event {} to topic {}", event.eventType, event.eventId, ORDER_EVENTS_TOPIC)

        val sendResult = kafkaTemplate.send(ORDER_EVENTS_TOPIC, key, value)
            .get(publishTimeoutSeconds, TimeUnit.SECONDS)
        logger.info(
            "Published {} event {} to topic {} partition {} offset {}",
            event.eventType,
            event.eventId,
            sendResult.recordMetadata.topic(),
            sendResult.recordMetadata.partition(),
            sendResult.recordMetadata.offset()
        )
    }
}
