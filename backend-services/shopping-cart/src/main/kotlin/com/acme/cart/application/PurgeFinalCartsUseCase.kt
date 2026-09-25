package com.acme.cart.application

import com.acme.cart.domain.events.CartPurged
import com.acme.cart.domain.events.CartPurgedPayload
import com.acme.cart.infrastructure.messaging.CartEventPublisher
import com.acme.cart.infrastructure.persistence.CartRepository
import com.acme.cart.infrastructure.persistence.FinalCart
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Deletes EXPIRED and MERGED carts, with their lines, once they have been final for longer
 * than the retention period (PIN-289; Epic 009 F1, "soft delete with retention"). ACTIVE
 * carts are never touched.
 *
 * Built like [ExpireIdleGuestCartsUseCase]: each cart is deleted by a conditional DELETE
 * that re-checks it is still final and old enough, and `CartPurged` is only published for
 * carts this run actually deleted. Publishing is best-effort, as for every cart event.
 */
@Service
class PurgeFinalCartsUseCase(
    private val cartRepository: CartRepository,
    private val eventPublisher: CartEventPublisher,
    @Value("\${acme.cart.retention}") private val retention: Duration,
    meterRegistry: MeterRegistry
) {
    init {
        // A zero or negative retention would put the cutoff at or after now and delete carts
        // the moment they expire or merge.
        require(retention > Duration.ZERO) { "acme.cart.retention must be positive, was $retention" }
    }

    private val logger = LoggerFactory.getLogger(PurgeFinalCartsUseCase::class.java)
    private val purgedCarts = meterRegistry.counter(PURGED_METRIC)

    /** Deletes every final cart past retention as of [now] and returns how many. */
    fun execute(now: Instant = Instant.now()): Int {
        val cutoff = now.minus(retention)
        val correlationId = UUID.randomUUID()
        var total = 0
        while (true) {
            val batch = cartRepository.findFinalCartsBefore(cutoff, PageRequest.of(0, BATCH_SIZE))
            val purged = batch.count { purge(it, cutoff, correlationId) }
            total += purged
            // A short batch was the last one; a batch that deleted nothing would be found again.
            if (batch.size < BATCH_SIZE || purged == 0) break
        }
        return total
    }

    private fun purge(cart: FinalCart, cutoff: Instant, correlationId: UUID): Boolean {
        if (cartRepository.deleteIfFinalBefore(cart.id, cutoff) == 0) return false
        purgedCarts.increment()
        eventPublisher.publishLoggingFailure(
            CartPurged.create(CartPurgedPayload(cart.id, cart.status, cart.finalizedAt), correlationId),
            logger
        )
        return true
    }

    companion object {
        const val BATCH_SIZE = 500

        /** Exposed by Prometheus-style registries as `cart_purged_total`. */
        const val PURGED_METRIC = "cart.purged"
    }
}
