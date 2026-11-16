package com.acme.cart.infrastructure.projection

import com.acme.cart.domain.CartStatus
import com.acme.cart.infrastructure.messaging.OutboxWriter
import com.acme.cart.infrastructure.persistence.CartRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

data class BackfillResult(val written: Int, val failed: Int)

/**
 * Gives every current cart that predates the read model a MongoDB document (PIN-551).
 *
 * The Debezium connector starts with `snapshot.mode: no_data`, so it publishes only the outbox rows
 * written after it started. A shopper whose cart has not changed since has no document, and
 * `GET /carts/current` answers 204 as if they had no cart. This writes a snapshot row to the outbox
 * for each ACTIVE or CHECKOUT cart, and the projector does the rest. Final carts are never served,
 * so they get none.
 *
 * It is off unless `acme.cart.read-model.backfill-on-startup` is true, so a run is deliberate. It is
 * safe to run again: a snapshot replaces the document. Run it before traffic, or when it is quiet: a
 * cart changed between the backfill reading its state and writing its row is overwritten with the
 * older state until that cart changes again.
 */
@Component
@ConditionalOnProperty(name = ["acme.cart.read-model.backfill-on-startup"], havingValue = "true")
class CartReadModelBackfill(
    private val cartRepository: CartRepository,
    private val outboxWriter: OutboxWriter,
    private val transactionTemplate: TransactionTemplate,
    @Value("\${acme.cart.read-model.backfill-page-size:500}")
    private val pageSize: Int
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(CartReadModelBackfill::class.java)

    override fun run(args: ApplicationArguments) {
        backfill()
    }

    fun backfill(): BackfillResult {
        logger.info("Backfilling the cart read model")
        var written = 0
        var failed = 0
        var pageNumber = 0
        do {
            val page = cartRepository.findIdsByStatusIn(CartStatus.CURRENT, PageRequest.of(pageNumber++, pageSize))
            page.content.forEach { cartId ->
                try {
                    transactionTemplate.executeWithoutResult { outboxWriter.writeSnapshot(cartId) }
                    written++
                } catch (e: Exception) {
                    failed++
                    logger.error("Could not backfill cart {}: {}", cartId, e.message, e)
                }
            }
        } while (page.hasNext())

        val result = BackfillResult(written, failed)
        logger.info("Backfilled the cart read model: {}", result)
        return result
    }
}
