package com.acme.notification.infrastructure.projection

import com.acme.notification.infrastructure.messaging.OutboxWriter
import com.acme.notification.infrastructure.persistence.NotificationDeliveryRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import java.util.UUID

data class BackfillResult(val total: Int, val written: Int, val failed: Int)

/**
 * Gives every delivery that predates the read model a MongoDB document (PIN-549).
 *
 * The Debezium connector starts with `snapshot.mode: no_data`, so it publishes only the outbox rows
 * written after it started. A delivery sent earlier has no document, so the lookups would not find
 * it. This writes a snapshot row to the outbox for each delivery, and the projector does the rest,
 * as it does for any change.
 *
 * It is off unless `notification.read-model.backfill-on-startup` is true, so a run is deliberate. It
 * is safe to run again: a snapshot replaces the document. Deliveries are not changed after they are
 * sent, so unlike the other services' backfills there is no write to race with.
 */
@Component
@ConditionalOnProperty(name = ["notification.read-model.backfill-on-startup"], havingValue = "true")
class DeliveryReadModelBackfill(
    private val deliveryRepository: NotificationDeliveryRepository,
    private val outboxWriter: OutboxWriter,
    @Value("\${notification.read-model.backfill-page-size:500}")
    private val pageSize: Int
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(DeliveryReadModelBackfill::class.java)

    override fun run(args: ApplicationArguments) {
        backfill()
    }

    fun backfill(): BackfillResult {
        logger.info("Backfilling the notification deliveries read model")
        var written = 0
        var failed = 0
        var total = 0
        var pageNumber = 0
        do {
            val page = deliveryRepository.findAll(PageRequest.of(pageNumber++, pageSize, Sort.by("id")))
            total = page.totalElements.toInt()
            page.content.forEach { delivery ->
                try {
                    outboxWriter.writeSnapshot(delivery, UUID.randomUUID())
                    written++
                } catch (e: Exception) {
                    failed++
                    logger.error("Could not backfill notification {}: {}", delivery.id, e.message, e)
                }
            }
        } while (page.hasNext())

        val result = BackfillResult(total, written, failed)
        logger.info("Backfilled the notification deliveries read model: {}", result)
        return result
    }
}
