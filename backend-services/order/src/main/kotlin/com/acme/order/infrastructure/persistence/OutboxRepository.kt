package com.acme.order.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface OutboxRepository : JpaRepository<OutboxMessage, UUID> {

    /**
     * Deletes the messages written before [cutoff] and returns how many (PIN-552). Debezium has
     * read them from the log by then, so nothing needs them in the table.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM OutboxMessage o WHERE o.createdAt < :cutoff")
    fun deleteCreatedBefore(@Param("cutoff") cutoff: Instant): Int
}
