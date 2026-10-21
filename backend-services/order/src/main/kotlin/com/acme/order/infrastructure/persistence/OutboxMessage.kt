package com.acme.order.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * An event waiting in the transactional outbox (PIN-368, ADR-0022).
 *
 * `OutboxWriter` saves it in the same transaction as the order change, and `OutboxRelay`
 * sends it to Kafka afterwards, so an order is never committed without its event.
 */
@Entity
@Table(name = "outbox")
class OutboxMessage(
    @Id
    val id: UUID = UUID.randomUUID(),

    /** Assigned by the database at insert (an identity column); null until reloaded. */
    @Column(name = "sequence_number", insertable = false, updatable = false)
    val sequenceNumber: Long? = null,

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
    val createdAt: Instant = Instant.now(),

    @Column(name = "published_at")
    var publishedAt: Instant? = null,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null
)
