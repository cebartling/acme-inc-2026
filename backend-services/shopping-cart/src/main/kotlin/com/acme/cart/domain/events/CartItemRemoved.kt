package com.acme.cart.domain.events

import java.time.Instant
import java.util.UUID

data class CartItemRemovedPayload(
    val cartId: UUID,
    val cartItemId: UUID,
    val variantId: UUID,
    /** Units on the line when it was removed. */
    val quantity: Int,
    val sessionId: String,
    val customerId: UUID?
)

class CartItemRemoved(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: CartItemRemovedPayload
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
        const val EVENT_TYPE = "CartItemRemoved"
        const val EVENT_VERSION = "1.0"
        const val AGGREGATE_TYPE = "Cart"

        fun create(payload: CartItemRemovedPayload, correlationId: UUID): CartItemRemoved =
            CartItemRemoved(
                eventId = UUID.randomUUID(),
                timestamp = Instant.now(),
                aggregateId = payload.cartId,
                correlationId = correlationId,
                payload = payload
            )
    }
}
