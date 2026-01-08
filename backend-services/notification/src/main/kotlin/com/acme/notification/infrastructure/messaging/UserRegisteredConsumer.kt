package com.acme.notification.infrastructure.messaging

import com.acme.notification.application.eventhandlers.UserRegisteredHandler
import com.acme.notification.infrastructure.messaging.dto.UserRegisteredEvent
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
 * Kafka consumer for UserRegistered events from the Identity Service.
 *
 * This consumer processes user registration events and triggers
 * the sending of verification emails. It implements idempotency
 * by tracking processed event IDs and uses manual acknowledgment
 * for reliable message processing.
 */
@Component
class UserRegisteredConsumer(
    private val userRegisteredHandler: UserRegisteredHandler,
    private val objectMapper: ObjectMapper,
    meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(UserRegisteredConsumer::class.java)

    private val eventsProcessedCounter: Counter = Counter.builder("notification.events.processed")
        .tag("event_type", "UserRegistered")
        .tag("status", "success")
        .register(meterRegistry)

    private val eventsSkippedCounter: Counter = Counter.builder("notification.events.processed")
        .tag("event_type", "UserRegistered")
        .tag("status", "skipped")
        .register(meterRegistry)

    private val eventsFailedCounter: Counter = Counter.builder("notification.events.processed")
        .tag("event_type", "UserRegistered")
        .tag("status", "failed")
        .register(meterRegistry)

    private val processingTimer: Timer = Timer.builder("notification.event.processing.duration")
        .tag("event_type", "UserRegistered")
        .register(meterRegistry)

    private val eventLagTimer: Timer = Timer.builder("notification.event.processing.lag")
        .tag("event_type", "UserRegistered")
        .register(meterRegistry)

    /**
     * Consumes UserRegistered events from Kafka.
     *
     * @param record The Kafka consumer record containing the event.
     * @param acknowledgment The acknowledgment handle for manual commit.
     */
    @KafkaListener(
        topics = ["\${notification.events.input-topic}"],
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun consume(record: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        val startTime = Instant.now()

        try {
            // First check event type before full deserialization to avoid parsing
            // non-UserRegistered events (like EmailVerified, UserActivated) which
            // have different payload structures
            val eventNode = objectMapper.readTree(record.value())
            val eventType = eventNode.get("eventType")?.asText()

            // Skip non-UserRegistered events silently (other consumers will handle them)
            if (eventType != UserRegisteredEvent.EVENT_TYPE) {
                acknowledgment.acknowledge()
                return
            }

            val event = objectMapper.treeToValue(eventNode, UserRegisteredEvent::class.java)

            logger.info(
                "Received {} event {} for user {} from partition {} offset {}",
                event.eventType,
                event.eventId,
                event.payload.userId,
                record.partition(),
                record.offset()
            )

            // Track event processing lag
            val eventAge = Duration.between(event.timestamp, startTime)
            eventLagTimer.record(eventAge)

            // Process the event
            processingTimer.record(Runnable {
                userRegisteredHandler.handle(event)
            })

            eventsProcessedCounter.increment()
            acknowledgment.acknowledge()

            val processingDuration = Duration.between(startTime, Instant.now())
            logger.info(
                "Successfully processed {} event {} for user {} in {}ms",
                event.eventType,
                event.eventId,
                event.payload.userId,
                processingDuration.toMillis()
            )

        } catch (e: Exception) {
            eventsFailedCounter.increment()
            logger.error(
                "Failed to process event from partition {} offset {}: {}",
                record.partition(),
                record.offset(),
                e.message,
                e
            )
            // Re-throw to trigger retry mechanism
            throw e
        }
    }
}
