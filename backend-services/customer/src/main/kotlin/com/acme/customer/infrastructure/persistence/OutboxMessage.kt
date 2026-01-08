package com.acme.customer.infrastructure.persistence

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Outbox message entity for the Transactional Outbox pattern.
 *
 * Messages are written to this table within the same transaction as
 * the domain state changes. A separate relay process polls for
 * unpublished messages and publishes them to Kafka asynchronously.
 *
 * This decouples Kafka availability from database transaction success,
 * ensuring reliable event publishing without distributed transactions.
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
