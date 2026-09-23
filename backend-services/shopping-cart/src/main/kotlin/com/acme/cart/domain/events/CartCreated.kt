package com.acme.cart.domain.events

import java.time.Instant
import java.util.UUID

data class CartCreatedPayload(
    val cartId: UUID,
    val sessionId: String?,
    val userId: UUID?
)

class CartCreated(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: CartCreatedPayload
) : DomainEvent(
    eventId = eventId,
    eventType = EVENT_TYPE,
    eventVersion = EVENT_VERSION,
    timestamp = timestamp,
    aggregateId = aggregateId,
    aggregateType = AGGREGATE_TYPE,
    correlationId = correlationId
) {
    companion object {
        const val EVENT_TYPE = "CartCreated"
        const val EVENT_VERSION = "1.0"
        const val AGGREGATE_TYPE = "Cart"

        fun create(
            cartId: UUID,
            sessionId: String?,
            userId: UUID?,
            correlationId: UUID
        ): CartCreated = CartCreated(
            eventId = UUID.randomUUID(),
            timestamp = Instant.now(),
            aggregateId = cartId,
            correlationId = correlationId,
            payload = CartCreatedPayload(cartId = cartId, sessionId = sessionId, userId = userId)
        )
    }
}
