package com.acme.customer.infrastructure.persistence

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for tracking processed events (idempotency).
 *
 * @property eventId The ID of the processed event.
 * @property eventType The type of the processed event.
 * @property processedAt When the event was processed.
 */
@Entity
@Table(name = "processed_events")
class ProcessedEvent(
    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    val eventId: UUID,

    @Column(name = "event_type", nullable = false, length = 100)
    val eventType: String,

    @Column(name = "processed_at", nullable = false)
    val processedAt: Instant = Instant.now()
)

/**
 * Repository for tracking processed events to ensure idempotency.
 *
 * Before processing a Kafka message, check if the event ID exists
 * in this table. If it does, the event has already been processed
 * and should be skipped.
 */
@Repository
interface ProcessedEventRepository : JpaRepository<ProcessedEvent, UUID> {

    /**
     * Checks if an event has already been processed.
     *
     * @param eventId The event ID to check.
     * @return True if the event has been processed.
     */
    fun existsByEventId(eventId: UUID): Boolean
}
