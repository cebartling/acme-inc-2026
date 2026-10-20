package com.acme.cart.domain.events

import java.time.Instant
import java.util.UUID

/** An order was placed for the cart's checkout session and the cart is CONVERTED (PIN-331, journey 0005). */
data class CartConvertedToOrderPayload(
    val cartId: UUID,
    /** The order the cart became. */
    val orderId: UUID,
    /** The checkout session the order was placed for. */
    val checkoutSessionId: UUID,
    /** Lines in the cart. */
    val lineCount: Int,
    /** Units across the cart's lines. */
    val itemCount: Int,
    val sessionId: String?,
    val userId: UUID?
)

class CartConvertedToOrder(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: CartConvertedToOrderPayload
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
        const val EVENT_TYPE = "CartConvertedToOrder"
        const val EVENT_VERSION = "1.0"
        const val AGGREGATE_TYPE = "Cart"

        fun create(payload: CartConvertedToOrderPayload, correlationId: UUID): CartConvertedToOrder =
            CartConvertedToOrder(
                eventId = UUID.randomUUID(),
                timestamp = Instant.now(),
                aggregateId = payload.cartId,
                correlationId = correlationId,
                payload = payload
            )
    }
}
