package com.acme.order.domain.events

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class Money(val amount: BigDecimal, val currency: String)

/** An order was placed and its cart converted (PIN-366, journey 0005 step 5's `OrderCreated`). */
data class OrderCreatedPayload(
    val orderId: UUID,
    val orderNumber: String,
    val customerId: UUID,
    val status: String,
    /** The subtotal: there is no shipping, tax or discount yet. */
    val total: Money,
    /** Units across the order's lines, as the cart counts them. */
    val itemCount: Int
)

class OrderCreated(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: OrderCreatedPayload
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
        const val EVENT_TYPE = "OrderCreated"
        const val EVENT_VERSION = "1.0"
        const val AGGREGATE_TYPE = "Order"

        /** The correlation ID is the checkout session's ID, which ties the order to its checkout. */
        fun create(payload: OrderCreatedPayload, correlationId: UUID, now: Instant): OrderCreated =
            OrderCreated(
                eventId = UUID.randomUUID(),
                timestamp = now,
                aggregateId = payload.orderId,
                correlationId = correlationId,
                payload = payload
            )
    }
}
