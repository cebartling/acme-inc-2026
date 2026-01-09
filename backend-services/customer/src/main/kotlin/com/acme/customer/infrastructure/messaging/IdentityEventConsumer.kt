package com.acme.customer.infrastructure.messaging

import com.acme.customer.application.eventhandlers.UserActivatedHandler
import com.acme.customer.application.eventhandlers.UserRegisteredHandler
import com.acme.customer.infrastructure.messaging.dto.UserActivatedEvent
import com.acme.customer.infrastructure.messaging.dto.UserRegisteredEvent
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
 * Unified Kafka consumer for Identity Service events.
 *
 * This consumer processes all events from the identity.user.events topic,
 * routing them to the appropriate handlers based on event type:
 * - UserRegistered: Creates new customer profiles
 * - UserActivated: Activates customer profiles after email verification
 *
 * Using a single consumer ensures all events are processed correctly
 * within the same consumer group, avoiding the issue of messages being
 * round-robined to consumers that can't handle them.
 */
@Component
class IdentityEventConsumer(
    private val userRegisteredHandler: UserRegisteredHandler,
    private val userActivatedHandler: UserActivatedHandler,
    private val objectMapper: ObjectMapper,
    meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(IdentityEventConsumer::class.java)

    private val userRegisteredProcessedCounter: Counter = Counter.builder("customer.events.processed")
        .tag("event_type", "UserRegistered")
        .tag("status", "success")
        .register(meterRegistry)

    private val userRegisteredFailedCounter: Counter = Counter.builder("customer.events.processed")
        .tag("event_type", "UserRegistered")
        .tag("status", "failed")
        .register(meterRegistry)

    private val userActivatedProcessedCounter: Counter = Counter.builder("customer.events.processed")
        .tag("event_type", "UserActivated")
        .tag("status", "success")
        .register(meterRegistry)

    private val userActivatedFailedCounter: Counter = Counter.builder("customer.events.processed")
        .tag("event_type", "UserActivated")
        .tag("status", "failed")
        .register(meterRegistry)

    private val unknownEventCounter: Counter = Counter.builder("customer.events.processed")
        .tag("event_type", "unknown")
        .tag("status", "skipped")
        .register(meterRegistry)

    private val processingTimer: Timer = Timer.builder("customer.event.processing.duration")
        .tag("consumer", "identity")
        .register(meterRegistry)

    private val eventLagTimer: Timer = Timer.builder("customer.event.processing.lag")
        .tag("consumer", "identity")
        .register(meterRegistry)

    /**
     * Consumes events from the Identity Service Kafka topic.
     *
     * Routes events to appropriate handlers based on eventType:
     * - UserRegistered -> UserRegisteredHandler
     * - UserActivated -> UserActivatedHandler
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
            val eventNode = objectMapper.readTree(record.value())
            val eventType = eventNode.get("eventType")?.asText()

            when (eventType) {
                UserRegisteredEvent.EVENT_TYPE -> {
                    val event = objectMapper.treeToValue(eventNode, UserRegisteredEvent::class.java)
                    processUserRegisteredEvent(event, record, startTime)
                    userRegisteredProcessedCounter.increment()
                }
                UserActivatedEvent.EVENT_TYPE -> {
                    val event = objectMapper.treeToValue(eventNode, UserActivatedEvent::class.java)
                    processUserActivatedEvent(event, record, startTime)
                    userActivatedProcessedCounter.increment()
                }
                else -> {
                    logger.debug(
                        "Ignoring unknown event type '{}' from partition {} offset {}",
                        eventType,
                        record.partition(),
                        record.offset()
                    )
                    unknownEventCounter.increment()
                }
            }

            acknowledgment.acknowledge()

        } catch (e: Exception) {
            // Determine which counter to increment based on partial parsing
            try {
                val eventNode = objectMapper.readTree(record.value())
                when (eventNode.get("eventType")?.asText()) {
                    UserRegisteredEvent.EVENT_TYPE -> userRegisteredFailedCounter.increment()
                    UserActivatedEvent.EVENT_TYPE -> userActivatedFailedCounter.increment()
                }
            } catch (_: Exception) {
                // Can't parse, just log
            }

            logger.error(
                "Failed to process event from partition {} offset {}: {}",
                record.partition(),
                record.offset(),
                e.message,
                e
            )
            throw e
        }
    }

    private fun processUserRegisteredEvent(
        event: UserRegisteredEvent,
        record: ConsumerRecord<String, String>,
        startTime: Instant
    ) {
        logger.info(
            "Received {} event {} for user {} from partition {} offset {}",
            event.eventType,
            event.eventId,
            event.payload.userId,
            record.partition(),
            record.offset()
        )

        val eventAge = Duration.between(event.timestamp, startTime)
        eventLagTimer.record(eventAge)

        processingTimer.record(Runnable {
            userRegisteredHandler.handle(event)
        })

        val processingDuration = Duration.between(startTime, Instant.now())
        logger.info(
            "Successfully processed {} event {} for user {} in {}ms",
            event.eventType,
            event.eventId,
            event.payload.userId,
            processingDuration.toMillis()
        )
    }

    private fun processUserActivatedEvent(
        event: UserActivatedEvent,
        record: ConsumerRecord<String, String>,
        startTime: Instant
    ) {
        logger.info(
            "Received {} event {} for user {} from partition {} offset {}",
            event.eventType,
            event.eventId,
            event.payload.userId,
            record.partition(),
            record.offset()
        )

        val eventAge = Duration.between(event.timestamp, startTime)
        eventLagTimer.record(eventAge)

        processingTimer.record(Runnable {
            userActivatedHandler.handle(event)
        })

        val processingDuration = Duration.between(startTime, Instant.now())
        logger.info(
            "Successfully processed {} event {} for user {} in {}ms",
            event.eventType,
            event.eventId,
            event.payload.userId,
            processingDuration.toMillis()
        )
    }
}
