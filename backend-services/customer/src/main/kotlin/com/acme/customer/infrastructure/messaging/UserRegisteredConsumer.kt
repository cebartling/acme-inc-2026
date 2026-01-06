package com.acme.customer.infrastructure.messaging

import com.acme.customer.application.eventhandlers.UserRegisteredHandler
import com.acme.customer.infrastructure.messaging.dto.UserRegisteredEvent
import com.acme.customer.infrastructure.persistence.ProcessedEvent
import com.acme.customer.infrastructure.persistence.ProcessedEventRepository
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
 * the creation of customer profiles. It implements idempotency
 * by tracking processed event IDs and uses manual acknowledgment
 * for reliable message processing.
 */
@Component
class UserRegisteredConsumer(
    private val userRegisteredHandler: UserRegisteredHandler,
    private val processedEventRepository: ProcessedEventRepository,
    private val objectMapper: ObjectMapper,
    meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(UserRegisteredConsumer::class.java)

    private val eventsProcessedCounter: Counter = Counter.builder("customer.events.processed")
        .tag("event_type", "UserRegistered")
        .tag("status", "success")
        .register(meterRegistry)

    private val eventsSkippedCounter: Counter = Counter.builder("customer.events.processed")
        .tag("event_type", "UserRegistered")
        .tag("status", "skipped")
        .register(meterRegistry)

    private val eventsFailedCounter: Counter = Counter.builder("customer.events.processed")
        .tag("event_type", "UserRegistered")
        .tag("status", "failed")
        .register(meterRegistry)

    private val processingTimer: Timer = Timer.builder("customer.event.processing.duration")
        .tag("event_type", "UserRegistered")
        .register(meterRegistry)

    private val eventLagTimer: Timer = Timer.builder("customer.event.processing.lag")
        .tag("event_type", "UserRegistered")
        .register(meterRegistry)

    /**
     * Consumes UserRegistered events from Kafka.
     *
     * @param record The Kafka consumer record containing the event.
     * @param acknowledgment The acknowledgment handle for manual commit.
     */
    @KafkaListener(
        topics = ["\${customer.events.input-topic}"],
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun consume(record: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        val startTime = Instant.now()

        try {
            val event = objectMapper.readValue(record.value(), UserRegisteredEvent::class.java)

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

            // Validate event type
            if (event.eventType != UserRegisteredEvent.EVENT_TYPE) {
                logger.warn(
                    "Unexpected event type: {} (expected: {}), skipping",
                    event.eventType,
                    UserRegisteredEvent.EVENT_TYPE
                )
                eventsSkippedCounter.increment()
                acknowledgment.acknowledge()
                return
            }

            // Process the event (idempotency check now happens inside the handler within transaction)
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
