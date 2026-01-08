package com.acme.customer.infrastructure.messaging

import com.acme.customer.domain.events.DomainEvent
import com.acme.customer.infrastructure.persistence.OutboxMessage
import com.acme.customer.infrastructure.persistence.OutboxRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Writes domain events to the outbox table.
 *
 * This component is responsible for persisting events to the outbox
 * within the same database transaction as domain state changes.
 * The outbox relay will later publish these events to Kafka asynchronously.
 *
 * This implements the write side of the Transactional Outbox pattern.
 */
@Component
class OutboxWriter(
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(OutboxWriter::class.java)

    /**
     * Writes a domain event to the outbox.
     *
     * The event is serialized to JSON and stored with metadata
     * for later publishing. This operation must be called within
     * a transaction to ensure atomicity with domain changes.
     *
     * @param event The domain event to write to outbox.
     * @param topic The Kafka topic to publish to.
     */
    fun write(event: DomainEvent, topic: String) {
        val messageKey = event.aggregateId.toString()
        val payload = objectMapper.writeValueAsString(event)

        val outboxMessage = OutboxMessage(
            aggregateId = event.aggregateId,
            aggregateType = event.aggregateType,
            eventType = event.eventType,
            eventId = event.eventId,
            topic = topic,
            messageKey = messageKey,
            payload = payload
        )

        outboxRepository.save(outboxMessage)

        logger.debug(
            "Wrote {} event {} for aggregate {} to outbox for topic {}",
            event.eventType,
            event.eventId,
            event.aggregateId,
            topic
        )
    }
}
