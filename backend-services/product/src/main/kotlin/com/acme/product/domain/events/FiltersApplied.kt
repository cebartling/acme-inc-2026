package com.acme.product.domain.events

import com.acme.product.domain.SearchFilters
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class FiltersAppliedPayload(
    val query: String,
    val categories: List<String>,
    val priceMin: BigDecimal?,
    val priceMax: BigDecimal?,
    val resultCount: Long,
    val sessionId: String? = null
)

class FiltersApplied(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: FiltersAppliedPayload
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
        const val EVENT_TYPE = "FiltersApplied"
        const val EVENT_VERSION = "1.0"
        const val AGGREGATE_TYPE = "Search"
        const val TOPIC = "product.events"

        fun create(
            query: String,
            filters: SearchFilters,
            resultCount: Long,
            sessionId: String? = null,
            correlationId: UUID = UUID.randomUUID()
        ): FiltersApplied = FiltersApplied(
            eventId = UUID.randomUUID(),
            timestamp = Instant.now(),
            aggregateId = UUID.randomUUID(),
            correlationId = correlationId,
            payload = FiltersAppliedPayload(
                query = query,
                categories = filters.categories,
                priceMin = filters.priceMin,
                priceMax = filters.priceMax,
                resultCount = resultCount,
                sessionId = sessionId
            )
        )
    }
}
