package com.acme.cart.application

import com.acme.cart.domain.events.CartPurged
import com.acme.cart.domain.events.CartPurgedPayload
import com.acme.cart.infrastructure.messaging.OutboxWriter
import com.acme.cart.infrastructure.persistence.CartRepository
import com.acme.cart.infrastructure.persistence.FinalCart
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Deletes EXPIRED, MERGED and CONVERTED (PIN-331) carts, with their lines, once they have
 * been final for longer than the retention period (PIN-289; Epic 009 F1, "soft delete with
 * retention"). ACTIVE and CHECKOUT carts are never touched.
 *
 * Built like [ExpireIdleGuestCartsUseCase]: each cart is deleted by a conditional DELETE
 * that re-checks it is still final and old enough, and `CartPurged` is only written to the
 * outbox for carts this run actually deleted, in the same transaction (PIN-384).
 */
@Service
class PurgeFinalCartsUseCase(
    private val cartRepository: CartRepository,
    private val outboxWriter: OutboxWriter,
    private val transactionTemplate: TransactionTemplate,
    @Value("\${acme.cart.retention}") private val retention: Duration,
    meterRegistry: MeterRegistry
) {
    init {
        // A zero or negative retention would put the cutoff at or after now and delete carts
        // the moment they expire or merge.
        require(retention > Duration.ZERO) { "acme.cart.retention must be positive, was $retention" }
    }

    private val purgedCarts = meterRegistry.counter(PURGED_METRIC)

    /** Deletes every final cart past retention as of [now] and returns how many. */
    fun execute(now: Instant = Instant.now()): Int {
        val cutoff = now.minus(retention)
        val correlationId = UUID.randomUUID()
        return drainInBatches(
            BATCH_SIZE,
            nextBatch = { cartRepository.findFinalCartsBefore(cutoff, PageRequest.of(0, BATCH_SIZE)) },
            act = { purge(it, cutoff, correlationId) }
        )
    }

    private fun purge(cart: FinalCart, cutoff: Instant, correlationId: UUID): Boolean {
        val purged = transactionTemplate.execute { purgeInTransaction(cart, cutoff, correlationId) } == true
        if (purged) purgedCarts.increment()
        return purged
    }

    /** The conditional DELETE and its event commit together (PIN-384). */
    private fun purgeInTransaction(cart: FinalCart, cutoff: Instant, correlationId: UUID): Boolean {
        if (cartRepository.deleteIfFinalBefore(cart.id, cutoff) == 0) return false
        outboxWriter.write(
            CartPurged.create(CartPurgedPayload(cart.id, cart.status, cart.finalizedAt), correlationId)
        )
        return true
    }

    companion object {
        const val BATCH_SIZE = 500

        /** Exposed by Prometheus-style registries as `cart_purged_total`. */
        const val PURGED_METRIC = "cart.purged"
    }
}
