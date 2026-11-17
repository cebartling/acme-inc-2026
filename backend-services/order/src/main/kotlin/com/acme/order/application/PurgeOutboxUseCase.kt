package com.acme.order.application

import com.acme.order.infrastructure.persistence.OutboxRepository
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant

/**
 * Deletes outbox rows older than [maxAge] (PIN-552, PIN-433, ADR-0022). Debezium publishes a
 * row from the write-ahead log, so the table only has to keep it long enough for anyone
 * debugging a missing event to look at it.
 */
@Service
class PurgeOutboxUseCase(
    private val outbox: OutboxRepository,
    private val transactionTemplate: TransactionTemplate,
    meterRegistry: MeterRegistry,
    @Value("\${acme.order.outbox.retention.max-age}") private val maxAge: Duration
) {
    private val purgedRows = meterRegistry.counter("outbox_rows_purged_total")

    init {
        require(!maxAge.isNegative && !maxAge.isZero) {
            "acme.order.outbox.retention.max-age must be positive, was $maxAge"
        }
    }

    /** Returns how many rows it deleted, and counts them in `outbox_rows_purged_total`. */
    fun execute(now: Instant = Instant.now()): Int {
        val purged = checkNotNull(transactionTemplate.execute { outbox.deleteCreatedBefore(now.minus(maxAge)) }) {
            "Purging the outbox returned no count"
        }
        purgedRows.increment(purged.toDouble())
        return purged
    }
}
