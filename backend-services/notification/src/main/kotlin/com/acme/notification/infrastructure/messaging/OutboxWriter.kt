package com.acme.notification.infrastructure.messaging

import com.acme.notification.domain.events.DomainEvent
import com.acme.notification.infrastructure.persistence.OutboxMessage
import com.acme.notification.infrastructure.persistence.OutboxRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Writes a notification event to the transactional outbox (PIN-549, ADR-0022), for Debezium's
 * outbox event router to publish to the event's topic, keyed by aggregate id.
 *
 * It joins the caller's transaction, so the event commits or rolls back with the change.
 */
@Component
class OutboxWriter(
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(OutboxWriter::class.java)

    @Transactional
    fun write(event: DomainEvent, topic: String) {
        outboxRepository.save(
            OutboxMessage(
                aggregateId = event.aggregateId,
                aggregateType = event.aggregateType,
                eventType = event.eventType,
                eventId = event.eventId,
                topic = topic,
                messageKey = event.aggregateId.toString(),
                payload = objectMapper.writeValueAsString(event)
            )
        )

        logger.debug(
            "Wrote {} event {} for aggregate {} to the outbox for topic {}",
            event.eventType, event.eventId, event.aggregateId, topic
        )
    }
}
