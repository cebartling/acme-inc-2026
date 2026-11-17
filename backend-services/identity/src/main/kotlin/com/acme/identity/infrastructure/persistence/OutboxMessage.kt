package com.acme.identity.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * An event in the transactional outbox (PIN-548, ADR-0022).
 *
 * `OutboxWriter` saves it in the same transaction as the change it describes, and Debezium
 * publishes it to Kafka from the write-ahead log, so a change is never committed without its event.
 */
@Entity
@Table(name = "outbox")
class OutboxMessage(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "aggregate_id", nullable = false)
    val aggregateId: UUID,

    @Column(name = "aggregate_type", nullable = false, length = 100)
    val aggregateType: String,

    @Column(name = "event_type", nullable = false, length = 100)
    val eventType: String,

    @Column(name = "event_id", nullable = false, unique = true)
    val eventId: UUID,

    @Column(name = "topic", nullable = false, length = 255)
    val topic: String,

    @Column(name = "message_key", nullable = false, length = 255)
    val messageKey: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    val payload: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
