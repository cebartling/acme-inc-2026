package com.acme.identity.infrastructure.persistence

import com.acme.identity.domain.events.DomainEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

@Repository
class EventStoreRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    fun append(event: DomainEvent) {
        val payloadJson = objectMapper.writeValueAsString(event)

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

    fun findByAggregateId(aggregateId: java.util.UUID): List<Map<String, Any>> {
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
}
