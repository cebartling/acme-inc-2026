package com.acme.identity.infrastructure.persistence

import com.acme.identity.domain.events.DomainEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import kotlin.reflect.full.memberProperties

/**
 * Repository for persisting domain events to the event store.
 *
 * Implements event sourcing by storing all domain events as immutable
 * records in PostgreSQL. Events are stored with their full payload as
 * JSONB, enabling flexible querying and event replay.
 *
 * Events must be persisted before the HTTP response is sent to ensure
 * eventual consistency with downstream systems.
 *
 * @property jdbcTemplate Spring JDBC template for database access.
 * @property objectMapper Jackson mapper for JSON serialization.
 */
@Repository
class EventStoreRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {

    /**
     * Appends a domain event to the event store.
     *
     * The event is serialized to JSON and stored with all metadata
     * required for event replay and distributed tracing.
     *
     * Only the payload property is serialized to JSON, not the entire event.
     * This ensures the stored data contains only domain-specific information,
     * with metadata stored in separate columns.
     *
     * @param event The domain event to persist.
     */
    fun append(event: DomainEvent) {
        // Extract and serialize only the payload property using Kotlin reflection
        val payloadProperty = event::class.memberProperties.find { it.name == "payload" }
            ?: throw IllegalArgumentException("Event ${event::class.simpleName} does not have a payload property")

        val payload = payloadProperty.getter.call(event)
        val payloadJson = objectMapper.writeValueAsString(payload)

        jdbcTemplate.update(
            """
            INSERT INTO event_store (
                event_id, event_type, event_version, timestamp,
                aggregate_id, aggregate_type, correlation_id, payload
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            """.trimIndent(),
            event.eventId,
            event.eventType,
            event.eventVersion,
            Timestamp.from(event.timestamp),
            event.aggregateId,
            event.aggregateType,
            event.correlationId,
            payloadJson
        )
    }

    /**
     * Retrieves all events for a specific aggregate.
     *
     * Events are returned in chronological order, suitable for
     * rebuilding aggregate state through event replay.
     *
     * @param aggregateId The ID of the aggregate to query.
     * @return List of event records as maps, ordered by timestamp.
     */
    fun findByAggregateId(aggregateId: java.util.UUID): List<Map<String, Any?>> {
        return jdbcTemplate.queryForList(
            """
            SELECT event_id, event_type, event_version, timestamp,
                   aggregate_id, aggregate_type, correlation_id, payload
            FROM event_store
            WHERE aggregate_id = ?
            ORDER BY timestamp ASC
            """.trimIndent(),
            aggregateId
        )
    }

    /**
     * Retrieves all events of a specific type for a specific aggregate.
     *
     * This is primarily used for testing to verify that specific events
     * were published for a given user/aggregate.
     *
     * @param eventType The type of event to query (e.g., "DeviceRemembered").
     * @param aggregateId The ID of the aggregate to query.
     * @return List of event records as maps, ordered by timestamp.
     */
    fun findByEventTypeAndAggregateId(eventType: String, aggregateId: java.util.UUID): List<Map<String, Any?>> {
        return jdbcTemplate.queryForList(
            """
            SELECT event_id, event_type, event_version, timestamp,
                   aggregate_id, aggregate_type, correlation_id, payload
            FROM event_store
            WHERE event_type = ? AND aggregate_id = ?
            ORDER BY timestamp ASC
            """.trimIndent(),
            eventType,
            aggregateId
        )
    }
}
