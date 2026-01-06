package com.acme.customer.infrastructure.persistence

import com.acme.customer.domain.events.DomainEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.util.UUID

/**
 * Repository for persisting domain events to the event store.
 *
 * The event store is append-only and provides the foundation for
 * event sourcing. Events are stored with their full metadata and
 * JSON payload for replay and audit purposes.
 */
@Repository
class EventStoreRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(EventStoreRepository::class.java)

    /**
     * Appends a domain event to the event store.
     *
     * The event is serialized to JSON and stored with all metadata.
     * This operation should be called within a transaction with
     * the aggregate write to ensure consistency.
     *
     * @param event The domain event to persist.
     */
    fun append(event: DomainEvent) {
        logger.debug(
            "Appending event {} of type {} for aggregate {}",
            event.eventId,
            event.eventType,
            event.aggregateId
        )

        val payloadJson = objectMapper.writeValueAsString(event)

        jdbcTemplate.update(
            """
            INSERT INTO event_store (
                event_id, event_type, event_version, timestamp,
                aggregate_id, aggregate_type, correlation_id, causation_id, payload
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            """.trimIndent(),
            event.eventId,
            event.eventType,
            event.eventVersion,
            Timestamp.from(event.timestamp),
            event.aggregateId,
            event.aggregateType,
            event.correlationId,
            event.causationId,
            payloadJson
        )

        logger.info(
            "Persisted {} event {} for aggregate {}",
            event.eventType,
            event.eventId,
            event.aggregateId
        )
    }

    /**
     * Retrieves all events for a specific aggregate.
     *
     * Events are returned in chronological order for replay.
     *
     * @param aggregateId The aggregate ID to query.
     * @return List of events as maps containing event data.
     */
    fun findByAggregateId(aggregateId: UUID): List<Map<String, Any?>> {
        return jdbcTemplate.queryForList(
            """
            SELECT event_id, event_type, event_version, timestamp,
                   aggregate_id, aggregate_type, correlation_id, causation_id, payload
            FROM event_store
            WHERE aggregate_id = ?
            ORDER BY timestamp ASC
            """.trimIndent(),
            aggregateId
        )
    }

    /**
     * Retrieves all events by correlation ID for distributed tracing.
     *
     * @param correlationId The correlation ID to query.
     * @return List of events as maps containing event data.
     */
    fun findByCorrelationId(correlationId: UUID): List<Map<String, Any?>> {
        return jdbcTemplate.queryForList(
            """
            SELECT event_id, event_type, event_version, timestamp,
                   aggregate_id, aggregate_type, correlation_id, causation_id, payload
            FROM event_store
            WHERE correlation_id = ?
            ORDER BY timestamp ASC
            """.trimIndent(),
            correlationId
        )
    }
}
