package com.acme.cart.domain.events

import java.time.Instant
import java.util.UUID

data class CartItemQuantityUpdatedPayload(
    val cartId: UUID,
    val cartItemId: UUID,
    val variantId: UUID,
    val previousQuantity: Int,
    val newQuantity: Int,
    val reason: String,
    val sessionId: String?,
    val userId: UUID?
)

class CartItemQuantityUpdated(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: CartItemQuantityUpdatedPayload
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
        const val EVENT_TYPE = "CartItemQuantityUpdated"
        const val EVENT_VERSION = "1.0"
        const val AGGREGATE_TYPE = "Cart"
        const val REASON_CUSTOMER_UPDATE = "CUSTOMER_UPDATE"

        fun create(payload: CartItemQuantityUpdatedPayload, correlationId: UUID): CartItemQuantityUpdated =
            CartItemQuantityUpdated(
                eventId = UUID.randomUUID(),
                timestamp = Instant.now(),
                aggregateId = payload.cartId,
                correlationId = correlationId,
                payload = payload
            )
    }
}
