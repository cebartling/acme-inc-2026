package com.acme.cart.domain.events

import com.acme.cart.domain.CartStatus
import java.time.Instant
import java.util.UUID

/**
 * Published when an EXPIRED or MERGED cart is deleted after the retention period (PIN-289).
 * Its lines are deleted with it; the earlier `CartExpired` or `CartMerged` event carries only
 * counts, not the lines.
 *
 * Unlike the other cart events it carries no guest session ID: a merged cart's session can
 * still be the live key to a newer guest cart, and nothing downstream needs it here.
 */
data class CartPurgedPayload(
    val cartId: UUID,
    /** EXPIRED or MERGED: the status the cart ended in. */
    val finalStatus: CartStatus,
    /** When the cart expired or merged. */
    val finalizedAt: Instant
)

class CartPurged(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: CartPurgedPayload
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
        const val EVENT_TYPE = "CartPurged"
        const val EVENT_VERSION = "1.0"
        const val AGGREGATE_TYPE = "Cart"

        fun create(payload: CartPurgedPayload, correlationId: UUID): CartPurged =
            CartPurged(
                eventId = UUID.randomUUID(),
                timestamp = Instant.now(),
                aggregateId = payload.cartId,
                correlationId = correlationId,
                payload = payload
            )
    }
}
