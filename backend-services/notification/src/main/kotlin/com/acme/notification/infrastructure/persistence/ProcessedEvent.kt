package com.acme.notification.infrastructure.persistence

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for tracking processed Kafka events.
 *
 * Used for idempotency - ensures that the same event is not
 * processed multiple times even if it is delivered more than once.
 *
 * @property eventId The unique identifier of the processed event.
 * @property eventType The type of event that was processed.
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

    @Column(name = "processed_at", nullable = false, updatable = false)
    val processedAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProcessedEvent) return false
        return eventId == other.eventId
    }

    override fun hashCode(): Int = eventId.hashCode()
}
