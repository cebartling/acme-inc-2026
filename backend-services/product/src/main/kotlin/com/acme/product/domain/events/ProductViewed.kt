package com.acme.product.domain.events

import java.time.Instant
import java.util.UUID

data class ProductViewedPayload(
    val productId: UUID,
    val slug: String,
    val sessionId: String? = null
)

class ProductViewed(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: ProductViewedPayload
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
        const val EVENT_TYPE = "ProductViewed"
        const val EVENT_VERSION = "1.0"
        const val AGGREGATE_TYPE = "Product"
        const val TOPIC = "product.events"

        fun create(
            productId: UUID,
            slug: String,
            sessionId: String? = null,
            correlationId: UUID = UUID.randomUUID()
        ): ProductViewed {
            return ProductViewed(
                eventId = UUID.randomUUID(),
                timestamp = Instant.now(),
                aggregateId = productId,
                correlationId = correlationId,
                payload = ProductViewedPayload(
                    productId = productId,
                    slug = slug,
                    sessionId = sessionId
                )
            )
        }
    }
}
