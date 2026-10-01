package com.acme.cart.domain.events

import java.time.Instant
import java.util.UUID

/** A cart locked for checkout (PIN-329, journey 0005 AC-1.8). */
data class CheckoutInitiatedPayload(
    val cartId: UUID,
    val checkoutSessionId: UUID,
    val expiresAt: Instant,
    /** Units across the cart's lines. */
    val itemCount: Int,
    val subtotal: Money,
    val sessionId: String?,
    val userId: UUID?
)

class CheckoutInitiated(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: CheckoutInitiatedPayload
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
        const val EVENT_TYPE = "CheckoutInitiated"
        const val EVENT_VERSION = "1.0"
        const val AGGREGATE_TYPE = "Cart"

        fun create(payload: CheckoutInitiatedPayload, correlationId: UUID): CheckoutInitiated =
            CheckoutInitiated(
                eventId = UUID.randomUUID(),
                timestamp = Instant.now(),
                aggregateId = payload.cartId,
                correlationId = correlationId,
                payload = payload
            )
    }
}
