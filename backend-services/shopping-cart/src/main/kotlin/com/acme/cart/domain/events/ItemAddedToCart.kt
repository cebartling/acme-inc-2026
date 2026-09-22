package com.acme.cart.domain.events

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class Money(val amount: BigDecimal, val currency: String)

data class ItemAddedToCartPayload(
    val cartId: UUID,
    val cartItemId: UUID,
    val productId: UUID,
    val variantId: UUID,
    val sku: String,
    val productName: String,
    /** Units added by this operation, not the line's new total. */
    val quantity: Int,
    val unitPrice: Money,
    val sessionId: String,
    val customerId: UUID?
)

class ItemAddedToCart(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: ItemAddedToCartPayload
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
        const val EVENT_TYPE = "ItemAddedToCart"
        const val EVENT_VERSION = "1.0"
        const val AGGREGATE_TYPE = "Cart"

        fun create(payload: ItemAddedToCartPayload, correlationId: UUID): ItemAddedToCart =
            ItemAddedToCart(
                eventId = UUID.randomUUID(),
                timestamp = Instant.now(),
                aggregateId = payload.cartId,
                correlationId = correlationId,
                payload = payload
            )
    }
}
