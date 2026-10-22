package com.acme.cart.infrastructure.messaging

import com.acme.cart.domain.events.DomainEvent
import com.acme.cart.infrastructure.persistence.OutboxMessage
import com.acme.cart.infrastructure.persistence.OutboxRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

internal const val CART_EVENTS_TOPIC = "cart.events"

/**
 * Writes a cart event to the transactional outbox (PIN-384, ADR-0022), for `OutboxRelay`
 * to send to `cart.events` keyed by aggregate id.
 *
 * It must run inside the caller's transaction, so the event commits or rolls back with the
 * cart change: called outside one, it throws `IllegalTransactionStateException`.
 */
@Component
class OutboxWriter(
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(OutboxWriter::class.java)

    @Transactional(propagation = Propagation.MANDATORY)
    fun write(event: DomainEvent) {
        outboxRepository.save(
            OutboxMessage(
                aggregateId = event.aggregateId,
                aggregateType = event.aggregateType,
                eventType = event.eventType,
                eventId = event.eventId,
                topic = CART_EVENTS_TOPIC,
                messageKey = event.aggregateId.toString(),
                payload = objectMapper.writeValueAsString(event)
            )
        )
        logger.debug("Wrote {} event {} to the outbox for topic {}", event.eventType, event.eventId, CART_EVENTS_TOPIC)
    }
}
