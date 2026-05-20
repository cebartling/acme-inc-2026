package com.acme.notification.infrastructure.messaging

import com.acme.notification.application.eventhandlers.PasswordResetRequestedHandler
import com.acme.notification.infrastructure.messaging.dto.PasswordResetRequestedEvent
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * Kafka consumer for PasswordResetRequested events from the Identity
 * Service. Other event types on the same topic are skipped silently so
 * separate consumers can coexist on `identity.user.events`.
 */
@Component
class PasswordResetRequestedConsumer(
    private val handler: PasswordResetRequestedHandler,
    private val objectMapper: ObjectMapper,
    meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val processedCounter: Counter = Counter.builder("notification.events.processed")
        .tag("event_type", "PasswordResetRequested")
        .tag("status", "success")
        .register(meterRegistry)

    private val failedCounter: Counter = Counter.builder("notification.events.processed")
        .tag("event_type", "PasswordResetRequested")
        .tag("status", "failed")
        .register(meterRegistry)

    private val processingTimer: Timer = Timer.builder("notification.event.processing.duration")
        .tag("event_type", "PasswordResetRequested")
        .register(meterRegistry)

    private val eventLagTimer: Timer = Timer.builder("notification.event.processing.lag")
        .tag("event_type", "PasswordResetRequested")
        .register(meterRegistry)

    @KafkaListener(
        topics = ["\${notification.events.input-topic}"],
        groupId = "\${notification.events.password-reset-group-id:notification-password-reset}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun consume(record: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        val startTime = Instant.now()
        try {
            val eventNode = objectMapper.readTree(record.value())
            val eventType = eventNode.get("eventType")?.asText()

            if (eventType != PasswordResetRequestedEvent.EVENT_TYPE) {
                acknowledgment.acknowledge()
                return
            }

            val event = objectMapper.treeToValue(eventNode, PasswordResetRequestedEvent::class.java)

            logger.info(
                "Received {} event {} for user {} from partition {} offset {}",
                event.eventType, event.eventId, event.payload.userId,
                record.partition(), record.offset()
            )

            eventLagTimer.record(Duration.between(event.timestamp, startTime))

            processingTimer.record(Runnable { handler.handle(event) })

            processedCounter.increment()
            acknowledgment.acknowledge()

            logger.info(
                "Successfully processed {} event {} for user {} in {}ms",
                event.eventType, event.eventId, event.payload.userId,
                Duration.between(startTime, Instant.now()).toMillis()
            )
        } catch (e: Exception) {
            failedCounter.increment()
            logger.error(
                "Failed to process event from partition {} offset {}: {}",
                record.partition(), record.offset(), e.message, e
            )
            throw e
        }
    }
}
