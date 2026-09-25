package com.acme.cart.domain.events

import java.time.Instant
import java.util.UUID

/** Published when an idle guest cart expires (PIN-287; Epic 009, Active -> Expired). */
data class CartExpiredPayload(
    val cartId: UUID,
    val sessionId: String,
    /** When the guest last used the cart. */
    val lastActiveAt: Instant,
    /** Lines left in the cart when it expired. */
    val itemCount: Int
)

class CartExpired(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: CartExpiredPayload
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
        const val EVENT_TYPE = "CartExpired"
        const val EVENT_VERSION = "1.0"
        const val AGGREGATE_TYPE = "Cart"

        fun create(payload: CartExpiredPayload, correlationId: UUID): CartExpired =
            CartExpired(
                eventId = UUID.randomUUID(),
                timestamp = Instant.now(),
                aggregateId = payload.cartId,
                correlationId = correlationId,
                payload = payload
            )
    }
}
