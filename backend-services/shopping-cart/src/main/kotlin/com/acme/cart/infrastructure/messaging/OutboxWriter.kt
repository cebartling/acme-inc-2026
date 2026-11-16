package com.acme.cart.infrastructure.messaging

import com.acme.cart.domain.events.DomainEvent
import com.acme.cart.infrastructure.persistence.OutboxMessage
import com.acme.cart.infrastructure.persistence.OutboxRepository
import com.acme.cart.infrastructure.projection.CartSnapshotFactory
import com.acme.cart.infrastructure.projection.CartSnapshotMessage
import com.acme.cart.infrastructure.projection.ReadAfter
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.UUID

internal const val CART_EVENTS_TOPIC = "cart.events"

/**
 * Writes a cart event to the transactional outbox (PIN-384, ADR-0022), for Debezium
 * to publish to `cart.events` keyed by aggregate id.
 *
 * Each event also produces a snapshot of the cart's full state, for the read model's
 * projector on `cart.read-model` (PIN-551). While handling a web request, it sets
 * [ReadAfter.HEADER] to the event's id, so a later read can wait for this write.
 *
 * It must run inside the caller's transaction, so the event commits or rolls back with the
 * cart change: called outside one, it throws `IllegalTransactionStateException`.
 */
@Component
class OutboxWriter(
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper,
    private val snapshotFactory: CartSnapshotFactory
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
        writeSnapshot(event)
        logger.debug("Wrote {} event {} to the outbox for topic {}", event.eventType, event.eventId, CART_EVENTS_TOPIC)
    }

    private fun writeSnapshot(event: DomainEvent) {
        outboxRepository.save(
            OutboxMessage(
                aggregateId = event.aggregateId,
                aggregateType = event.aggregateType,
                eventType = CartSnapshotMessage.EVENT_TYPE,
                eventId = UUID.randomUUID(),
                topic = CartSnapshotMessage.TOPIC,
                messageKey = event.aggregateId.toString(),
                payload = objectMapper.writeValueAsString(snapshotFactory.build(event.aggregateId, event.eventId))
            )
        )
        // The response isn't committed yet: this runs inside the use case. A later event in the
        // same request replaces the header, because its snapshot includes the earlier changes.
        val response = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.response
        response?.setHeader(ReadAfter.HEADER, event.eventId.toString())
    }
}
