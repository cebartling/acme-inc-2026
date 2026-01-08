package com.acme.customer.infrastructure.messaging

import com.acme.customer.application.eventhandlers.UserActivatedHandler
import com.acme.customer.infrastructure.messaging.dto.UserActivatedEvent
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
 * Kafka consumer for UserActivated events from the Identity Service.
 *
 * This consumer processes user activation events and triggers
 * the activation of customer profiles. It implements idempotency
 * by tracking processed event IDs and uses manual acknowledgment
 * for reliable message processing.
 */
@Component
class UserActivatedConsumer(
    private val userActivatedHandler: UserActivatedHandler,
    private val objectMapper: ObjectMapper,
    meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(UserActivatedConsumer::class.java)

    private val eventsProcessedCounter: Counter = Counter.builder("customer.events.processed")
        .tag("event_type", "UserActivated")
        .tag("status", "success")
        .register(meterRegistry)

    private val eventsSkippedCounter: Counter = Counter.builder("customer.events.processed")
        .tag("event_type", "UserActivated")
        .tag("status", "skipped")
        .register(meterRegistry)

    private val eventsFailedCounter: Counter = Counter.builder("customer.events.processed")
        .tag("event_type", "UserActivated")
        .tag("status", "failed")
        .register(meterRegistry)

    private val processingTimer: Timer = Timer.builder("customer.event.processing.duration")
        .tag("event_type", "UserActivated")
        .register(meterRegistry)

    private val eventLagTimer: Timer = Timer.builder("customer.event.processing.lag")
        .tag("event_type", "UserActivated")
        .register(meterRegistry)

    /**
     * Consumes UserActivated events from Kafka.
     *
     * @param record The Kafka consumer record containing the event.
     * @param acknowledgment The acknowledgment handle for manual commit.
     */
    @KafkaListener(
        topics = ["\${customer.events.input-topic}"],
        containerFactory = "userActivatedKafkaListenerContainerFactory"
    )
    fun consume(record: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        val startTime = Instant.now()

        try {
            // First try to parse to check the event type
            val eventNode = objectMapper.readTree(record.value())
            val eventType = eventNode.get("eventType")?.asText()

            // Skip non-UserActivated events
            if (eventType != UserActivatedEvent.EVENT_TYPE) {
                // Not a UserActivated event, skip silently (other consumers will handle it)
                acknowledgment.acknowledge()
                return
            }

            val event = objectMapper.treeToValue(eventNode, UserActivatedEvent::class.java)

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

            // Process the event (idempotency check now happens inside the handler within transaction)
            processingTimer.record(Runnable {
                userActivatedHandler.handle(event)
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
