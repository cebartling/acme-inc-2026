package com.acme.notification.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * JPA repository for [ProcessedEvent] entities.
 *
 * Used for idempotency tracking of Kafka events.
 */
@Repository
interface ProcessedEventRepository : JpaRepository<ProcessedEvent, UUID> {

    /**
     * Checks if an event has already been processed.
     *
     * @param eventId The unique identifier of the event.
     * @return true if the event has been processed.
     */
    fun existsByEventId(eventId: UUID): Boolean
}
