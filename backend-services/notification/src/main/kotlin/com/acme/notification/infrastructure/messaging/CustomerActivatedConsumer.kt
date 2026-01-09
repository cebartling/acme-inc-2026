package com.acme.notification.infrastructure.messaging

import com.acme.notification.application.eventhandlers.CustomerActivatedHandler
import com.acme.notification.infrastructure.messaging.dto.CustomerActivatedEvent
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
 * Kafka consumer for CustomerActivated events from the Customer Service.
 *
 * This consumer processes customer activation events and triggers
 * the sending of welcome emails. It implements idempotency
 * by tracking processed event IDs and uses manual acknowledgment
 * for reliable message processing.
 */
@Component
class CustomerActivatedConsumer(
    private val customerActivatedHandler: CustomerActivatedHandler,
    private val objectMapper: ObjectMapper,
    meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(CustomerActivatedConsumer::class.java)

    private val eventsProcessedCounter: Counter = Counter.builder("notification.events.processed")
        .tag("event_type", "CustomerActivated")
        .tag("status", "success")
        .register(meterRegistry)

    private val eventsSkippedCounter: Counter = Counter.builder("notification.events.processed")
        .tag("event_type", "CustomerActivated")
        .tag("status", "skipped")
        .register(meterRegistry)

    private val eventsFailedCounter: Counter = Counter.builder("notification.events.processed")
        .tag("event_type", "CustomerActivated")
        .tag("status", "failed")
        .register(meterRegistry)

    private val processingTimer: Timer = Timer.builder("notification.event.processing.duration")
        .tag("event_type", "CustomerActivated")
        .register(meterRegistry)

    private val eventLagTimer: Timer = Timer.builder("notification.event.processing.lag")
        .tag("event_type", "CustomerActivated")
        .register(meterRegistry)

    /**
     * Consumes CustomerActivated events from Kafka.
     *
     * @param record The Kafka consumer record containing the event.
     * @param acknowledgment The acknowledgment handle for manual commit.
     */
    @KafkaListener(
        topics = ["\${notification.events.customer-input-topic}"],
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun consume(record: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        val startTime = Instant.now()

        try {
            // First check event type before full deserialization to avoid parsing
            // non-CustomerActivated events (like CustomerCreated) which
            // have different payload structures
            val eventNode = objectMapper.readTree(record.value())
            val eventType = eventNode.get("eventType")?.asText()

            // Skip non-CustomerActivated events silently (other consumers will handle them)
            if (eventType != CustomerActivatedEvent.EVENT_TYPE) {
                eventsSkippedCounter.increment()
                acknowledgment.acknowledge()
                return
            }

            val event = objectMapper.treeToValue(eventNode, CustomerActivatedEvent::class.java)

            logger.info(
                "Received {} event {} for customer {} from partition {} offset {}",
                event.eventType,
                event.eventId,
                event.payload.customerId,
                record.partition(),
                record.offset()
            )

            // Track event processing lag
            val eventAge = Duration.between(event.timestamp, startTime)
            eventLagTimer.record(eventAge)

            // Process the event
            processingTimer.record(Runnable {
                customerActivatedHandler.handle(event)
            })

            eventsProcessedCounter.increment()
            acknowledgment.acknowledge()

            val processingDuration = Duration.between(startTime, Instant.now())
            logger.info(
                "Successfully processed {} event {} for customer {} in {}ms",
                event.eventType,
                event.eventId,
                event.payload.customerId,
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
