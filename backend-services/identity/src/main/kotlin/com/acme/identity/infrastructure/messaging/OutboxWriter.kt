package com.acme.identity.infrastructure.messaging

import com.acme.identity.domain.events.DomainEvent
import com.acme.identity.infrastructure.persistence.OutboxMessage
import com.acme.identity.infrastructure.persistence.OutboxRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Writes an identity event to the transactional outbox (PIN-548, ADR-0022), for Debezium's
 * outbox event router to publish to the event's topic, keyed by aggregate id.
 *
 * It joins the caller's transaction, so the event commits or rolls back with the change. Session,
 * device and token-reuse events describe state in Redis and are written outside any transaction;
 * the write then opens its own, so the event is saved but is not atomic with the Redis change.
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
