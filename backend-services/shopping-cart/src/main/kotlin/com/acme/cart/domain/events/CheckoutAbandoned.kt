package com.acme.cart.domain.events

import java.time.Instant
import java.util.UUID

/** The customer left checkout and the cart is ACTIVE again (PIN-330, journey 0005). */
data class CheckoutAbandonedPayload(
    val cartId: UUID,
    /** The checkout session that was left. */
    val checkoutSessionId: UUID,
    /** Units across the cart's lines. */
    val itemCount: Int,
    val sessionId: String?,
    val userId: UUID?
)

class CheckoutAbandoned(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: CheckoutAbandonedPayload
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
        const val EVENT_TYPE = "CheckoutAbandoned"
        const val EVENT_VERSION = "1.0"
        const val AGGREGATE_TYPE = "Cart"

        fun create(payload: CheckoutAbandonedPayload, correlationId: UUID): CheckoutAbandoned =
            CheckoutAbandoned(
                eventId = UUID.randomUUID(),
                timestamp = Instant.now(),
                aggregateId = payload.cartId,
                correlationId = correlationId,
                payload = payload
            )
    }
}
