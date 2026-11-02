package com.acme.order.domain.events

import java.time.Instant
import java.util.UUID

/** A customer cancelled a submitted order (PIN-429). A failed placement announces nothing. */
data class OrderCancelledPayload(
    val orderId: UUID,
    val orderNumber: String,
    /** Identity's user ID, as in `OrderCreated`. */
    val userId: UUID,
    val reason: String
)

class OrderCancelled(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: OrderCancelledPayload
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
        const val EVENT_TYPE = "OrderCancelled"
        const val EVENT_VERSION = "1.0"
        const val AGGREGATE_TYPE = "Order"

        /** The correlation ID is the checkout session's ID, as in `OrderCreated`. */
        fun create(payload: OrderCancelledPayload, correlationId: UUID, now: Instant): OrderCancelled =
            OrderCancelled(
                eventId = UUID.randomUUID(),
                timestamp = now,
                aggregateId = payload.orderId,
                correlationId = correlationId,
                payload = payload
            )
    }
}
