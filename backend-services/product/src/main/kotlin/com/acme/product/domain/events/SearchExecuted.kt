package com.acme.product.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Payload for the [SearchExecuted] domain event.
 *
 * @property query The search query string.
 * @property totalResults Number of results returned.
 * @property page Page number that was requested.
 * @property executionTimeMs How long the search took.
 * @property sessionId Optional session identifier for analytics.
 */
data class SearchExecutedPayload(
    val query: String,
    val totalResults: Long,
    val page: Int,
    val executionTimeMs: Long,
    val sessionId: String? = null
)

/**
 * Domain event published when a product search is executed.
 *
 * Published to the [TOPIC] Kafka topic for downstream analytics consumers.
 *
 * @property payload The event payload.
 */
class SearchExecuted(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: SearchExecutedPayload
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
        const val EVENT_TYPE = "SearchExecuted"
        const val EVENT_VERSION = "1.0"
        const val AGGREGATE_TYPE = "Search"
        const val TOPIC = "product.events"

        /**
         * Factory method to create a new [SearchExecuted] event.
         */
        fun create(
            query: String,
            totalResults: Long,
            page: Int,
            executionTimeMs: Long,
            sessionId: String? = null,
            correlationId: UUID = UUID.randomUUID()
        ): SearchExecuted {
            val eventId = UUID.randomUUID()
            val aggregateId = UUID.randomUUID()
            val timestamp = Instant.now()

            return SearchExecuted(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = aggregateId,
                correlationId = correlationId,
                payload = SearchExecutedPayload(
                    query = query,
                    totalResults = totalResults,
                    page = page,
                    executionTimeMs = executionTimeMs,
                    sessionId = sessionId
                )
            )
        }
    }
}
