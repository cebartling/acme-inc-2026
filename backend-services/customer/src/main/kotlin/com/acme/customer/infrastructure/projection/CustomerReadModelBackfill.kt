package com.acme.customer.infrastructure.projection

import com.acme.customer.infrastructure.messaging.OutboxWriter
import com.acme.customer.infrastructure.persistence.CustomerRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component

data class BackfillResult(val total: Int, val written: Int, val failed: Int)

/**
 * Gives every customer that predates the read model a MongoDB document.
 *
 * The Debezium connector starts with `snapshot.mode: no_data`, so it publishes only the outbox rows
 * written after it started. A customer who has not changed since has no document, and reads of them
 * answer 404. This writes a snapshot row to the outbox for each customer, and the projector does the
 * rest, as it does for any change.
 *
 * It is off unless `customer.read-model.backfill-on-startup` is true, so a run is deliberate. It is
 * safe to run again: a snapshot replaces the document. Run it before traffic, or when it is quiet. A
 * customer's own change that commits between the backfill reading its state and writing its row is
 * overwritten with the older state, until that customer changes again.
 */
@Component
@ConditionalOnProperty(name = ["customer.read-model.backfill-on-startup"], havingValue = "true")
class CustomerReadModelBackfill(
    private val customerRepository: CustomerRepository,
    private val outboxWriter: OutboxWriter,
    @Value("\${customer.read-model.backfill-page-size:500}")
    private val pageSize: Int
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(CustomerReadModelBackfill::class.java)

    override fun run(args: ApplicationArguments) {
        backfill()
    }

    fun backfill(): BackfillResult {
        logger.info("Backfilling the customer read model")
        var written = 0
        var failed = 0
        var total = 0
        var pageNumber = 0
        do {
            val page = customerRepository.findAll(PageRequest.of(pageNumber++, pageSize, Sort.by("id")))
            total = page.totalElements.toInt()
            page.content.forEach { customer ->
                try {
                    outboxWriter.writeSnapshot(customer.id, AGGREGATE_TYPE)
                    written++
                } catch (e: Exception) {
                    failed++
                    logger.error("Could not backfill customer {}: {}", customer.id, e.message, e)
                }
            }
        } while (page.hasNext())

        val result = BackfillResult(total, written, failed)
        logger.info("Backfilled the customer read model: {}", result)
        return result
    }

    private companion object {
        const val AGGREGATE_TYPE = "Customer"
    }
}
