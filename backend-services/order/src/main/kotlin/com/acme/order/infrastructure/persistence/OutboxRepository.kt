package com.acme.order.infrastructure.persistence

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface OutboxRepository : JpaRepository<OutboxMessage, UUID> {

    /**
     * Unsent messages in the order they were written (by `sequenceNumber`), one page at a
     * time. A message is never left out however often it has failed, so a later event can't
     * be sent ahead of it.
     */
    @Query("SELECT o FROM OutboxMessage o WHERE o.publishedAt IS NULL ORDER BY o.sequenceNumber ASC")
    fun findUnsent(pageable: Pageable): List<OutboxMessage>
}
