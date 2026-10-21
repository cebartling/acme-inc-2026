package com.acme.cart.application

import com.acme.cart.domain.events.CheckoutSessionExpiredPayload
import com.acme.cart.infrastructure.persistence.CartRepository
import com.acme.cart.infrastructure.persistence.LapsedCheckout
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Unlocks carts whose checkout session lapsed without activity (PIN-330, journey 0005 E6): each
 * is ACTIVE again with its lines, so its owner can change it or start checkout over. Guest and
 * user carts alike, including a guest cart stranded in checkout when its owner signed in.
 *
 * Each cart is unlocked by a conditional UPDATE that re-checks the session is still lapsed, so
 * a session resumed between the scan and the update survives, and `CheckoutSessionExpired` is
 * only published for carts this run unlocked (see [CheckoutSessionExpiry]).
 *
 * A session is only unlocked once it has been lapsed for [grace] (PIN-383): the order service
 * reads a live session, places the order, then converts the cart, and a session that lapses
 * in between must still be locked when convert arrives, or the order is left with an ACTIVE
 * cart. The session is not live again meanwhile; only the cart stays locked.
 */
@Service
class UnlockLapsedCheckoutsUseCase(
    private val cartRepository: CartRepository,
    private val checkoutSessionExpiry: CheckoutSessionExpiry,
    @Value("\${acme.cart.checkout-unlock.grace}") private val grace: Duration
) {
    /** Unlocks every cart whose checkout session lapsed [grace] before [now] and returns how many. */
    fun execute(now: Instant = Instant.now()): Int {
        val correlationId = UUID.randomUUID()
        val cutoff = now.minus(grace)
        return drainInBatches(
            BATCH_SIZE,
            nextBatch = { cartRepository.findLapsedCheckouts(cutoff, PageRequest.of(0, BATCH_SIZE)) },
            act = { unlock(it, cutoff, now, correlationId) }
        )
    }

    private fun unlock(cart: LapsedCheckout, cutoff: Instant, now: Instant, correlationId: UUID): Boolean {
        if (cartRepository.unlockIfLapsed(cart.id, cutoff, now) == 0) return false
        checkoutSessionExpiry.report(
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
        )
        return true
    }

    companion object {
        const val BATCH_SIZE = 500
    }
}
