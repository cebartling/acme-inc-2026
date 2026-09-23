package com.acme.cart.domain.events

import java.time.Instant
import java.util.UUID

/** Published when a guest cart's lines move into a signed-in user's cart (AC-0004-08-10). */
data class CartMergedPayload(
    val targetCartId: UUID,
    val sourceCartId: UUID,
    val userId: UUID,
    val itemsMerged: Int,
    /** How many merged lines were capped at the maximum order quantity. */
    val quantitiesAdjusted: Int
)

class CartMerged(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: CartMergedPayload
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
        const val EVENT_TYPE = "CartMerged"
        const val EVENT_VERSION = "1.0"
        const val AGGREGATE_TYPE = "Cart"

        fun create(payload: CartMergedPayload, correlationId: UUID): CartMerged =
            CartMerged(
                eventId = UUID.randomUUID(),
                timestamp = Instant.now(),
                aggregateId = payload.targetCartId,
                correlationId = correlationId,
                payload = payload
            )
    }
}
