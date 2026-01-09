package com.acme.customer.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Repository for managing outbox messages.
 *
 * Provides methods to save messages during transactions and
 * query for unpublished messages for the relay process.
 */
@Repository
interface OutboxRepository : JpaRepository<OutboxMessage, UUID> {
    
    /**
     * Finds all messages that have not yet been published.
     *
     * Orders by creation time to maintain event ordering.
     * Used by the outbox relay to poll for messages to publish.
     *
     * @return List of unpublished outbox messages.
     */
    @Query("SELECT o FROM OutboxMessage o WHERE o.publishedAt IS NULL ORDER BY o.createdAt ASC")
    fun findUnpublished(): List<OutboxMessage>
}
