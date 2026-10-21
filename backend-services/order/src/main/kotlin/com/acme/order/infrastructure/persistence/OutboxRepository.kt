package com.acme.order.infrastructure.persistence

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface OutboxRepository : JpaRepository<OutboxMessage, UUID> {

    /**
     * Unsent messages that still have attempts left, oldest first, one page at a time.
     * A message that has used up its attempts is left out, so it isn't fetched again on
     * every poll (it stays in the table with its `lastError` for someone to look at).
     */
    @Query(
        "SELECT o FROM OutboxMessage o " +
            "WHERE o.publishedAt IS NULL AND o.retryCount < :maxAttempts " +
            "ORDER BY o.createdAt ASC"
    )
    fun findSendable(@Param("maxAttempts") maxAttempts: Int, pageable: Pageable): List<OutboxMessage>
}
