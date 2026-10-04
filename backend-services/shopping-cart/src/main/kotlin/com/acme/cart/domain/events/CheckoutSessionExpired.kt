package com.acme.cart.domain.events

import java.time.Instant
import java.util.UUID

/**
 * A checkout session lapsed without activity and its cart was unlocked, ACTIVE again with its
 * lines (PIN-330, journey 0005 E6).
 */
data class CheckoutSessionExpiredPayload(
    val cartId: UUID,
    val checkoutSessionId: UUID,
    /** When the session lapsed; the cart was unlocked at the event's timestamp. */
    val expiredAt: Instant,
    /** Lines in the cart, not units. */
    val lineCount: Int,
    val sessionId: String?,
    val userId: UUID?
)

class CheckoutSessionExpired(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: CheckoutSessionExpiredPayload
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
        const val EVENT_TYPE = "CheckoutSessionExpired"
        const val EVENT_VERSION = "1.0"
        const val AGGREGATE_TYPE = "Cart"

        fun create(payload: CheckoutSessionExpiredPayload, correlationId: UUID): CheckoutSessionExpired =
            CheckoutSessionExpired(
                eventId = UUID.randomUUID(),
                timestamp = Instant.now(),
                aggregateId = payload.cartId,
                correlationId = correlationId,
                payload = payload
            )
    }
}
