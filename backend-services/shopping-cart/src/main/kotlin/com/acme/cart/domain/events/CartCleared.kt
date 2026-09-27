package com.acme.cart.domain.events

import java.time.Instant
import java.util.UUID

data class CartClearedPayload(
    val cartId: UUID,
    /** Lines removed. */
    val lineCount: Int,
    /** Units across the removed lines. */
    val itemCount: Int,
    val sessionId: String?,
    val userId: UUID?
)

class CartCleared(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: CartClearedPayload
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
        const val EVENT_TYPE = "CartCleared"
        const val EVENT_VERSION = "1.0"
        const val AGGREGATE_TYPE = "Cart"

        fun create(payload: CartClearedPayload, correlationId: UUID): CartCleared =
            CartCleared(
                eventId = UUID.randomUUID(),
                timestamp = Instant.now(),
                aggregateId = payload.cartId,
                correlationId = correlationId,
                payload = payload
            )
    }
}
