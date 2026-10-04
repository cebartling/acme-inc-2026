package com.acme.cart.application

import com.acme.cart.domain.events.CheckoutSessionExpired
import com.acme.cart.domain.events.CheckoutSessionExpiredPayload
import com.acme.cart.infrastructure.messaging.CartEventPublisher
import com.acme.cart.infrastructure.persistence.CartRepository
import com.acme.cart.infrastructure.persistence.LapsedCheckout
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Unlocks carts whose checkout session lapsed without activity (PIN-330, journey 0005 E6): each
 * is ACTIVE again with its lines, so its owner can change it or start checkout over. Guest and
 * user carts alike, including a guest cart stranded in checkout when its owner signed in.
 *
 * Each cart is unlocked by a conditional UPDATE that re-checks the session is still lapsed, so
 * a session resumed between the scan and the update survives, and `CheckoutSessionExpired` is
 * only published for carts this run unlocked. Publishing is best-effort, as for every cart event.
 */
@Service
class UnlockLapsedCheckoutsUseCase(
    private val cartRepository: CartRepository,
    private val eventPublisher: CartEventPublisher,
    meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(UnlockLapsedCheckoutsUseCase::class.java)
    private val expiredSessions = meterRegistry.counter(EXPIRED_METRIC)

    /** Unlocks every cart whose checkout session lapsed before [now] and returns how many. */
    fun execute(now: Instant = Instant.now()): Int {
        val correlationId = UUID.randomUUID()
        var total = 0
        while (true) {
            val batch = cartRepository.findLapsedCheckouts(now, PageRequest.of(0, BATCH_SIZE))
            val unlocked = batch.count { unlock(it, now, correlationId) }
            total += unlocked
            // A short batch was the last one; a batch that unlocked nothing would be found again.
            if (batch.size < BATCH_SIZE || unlocked == 0) break
        }
        return total
    }

    private fun unlock(cart: LapsedCheckout, now: Instant, correlationId: UUID): Boolean {
        if (cartRepository.unlockIfLapsed(cart.id, now) == 0) return false
        expiredSessions.increment()
        eventPublisher.publishLoggingFailure(
            CheckoutSessionExpired.create(
                CheckoutSessionExpiredPayload(
                    cartId = cart.id,
                    checkoutSessionId = cart.checkoutSessionId,
                    expiredAt = cart.expiredAt,
                    lineCount = cart.lineCount,
                    itemCount = cart.itemCount.toInt(),
                    sessionId = cart.sessionId,
                    userId = cart.userId
                ),
                correlationId
            ),
            logger
        )
        return true
    }

    companion object {
        const val BATCH_SIZE = 500

        /** Exposed by Prometheus-style registries as `cart_checkout_expired_total`. */
        const val EXPIRED_METRIC = "cart.checkout.expired"
    }
}
