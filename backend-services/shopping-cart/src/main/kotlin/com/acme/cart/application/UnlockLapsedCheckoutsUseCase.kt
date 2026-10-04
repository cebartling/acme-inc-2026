package com.acme.cart.application

import com.acme.cart.domain.events.CheckoutSessionExpiredPayload
import com.acme.cart.infrastructure.persistence.CartRepository
import com.acme.cart.infrastructure.persistence.LapsedCheckout
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
 * only published for carts this run unlocked (see [CheckoutSessionExpiry]).
 */
@Service
class UnlockLapsedCheckoutsUseCase(
    private val cartRepository: CartRepository,
    private val checkoutSessionExpiry: CheckoutSessionExpiry
) {
    /** Unlocks every cart whose checkout session lapsed before [now] and returns how many. */
    fun execute(now: Instant = Instant.now()): Int {
        val correlationId = UUID.randomUUID()
        return drainInBatches(
            BATCH_SIZE,
            nextBatch = { cartRepository.findLapsedCheckouts(now, PageRequest.of(0, BATCH_SIZE)) },
            act = { unlock(it, now, correlationId) }
        )
    }

    private fun unlock(cart: LapsedCheckout, now: Instant, correlationId: UUID): Boolean {
        if (cartRepository.unlockIfLapsed(cart.id, now) == 0) return false
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
