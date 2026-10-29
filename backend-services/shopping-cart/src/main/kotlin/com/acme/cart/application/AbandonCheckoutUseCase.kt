package com.acme.cart.application

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartError
import com.acme.cart.domain.CartOwner
import com.acme.cart.domain.CheckoutSession
import com.acme.cart.domain.events.CheckoutAbandoned
import com.acme.cart.domain.events.CheckoutAbandonedPayload
import com.acme.cart.infrastructure.messaging.OutboxWriter
import com.acme.cart.infrastructure.persistence.CartRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

data class AbandonCheckoutCommand(val owner: CartOwner, val cartId: UUID)

/**
 * Leaves checkout on the caller's own cart (PIN-330): the cart is ACTIVE again with its lines,
 * and `CheckoutAbandoned` is written to the outbox in the same transaction. A session that had already lapsed, but
 * that the unlock job had not reached yet, is reported as expired instead: it ended by timing
 * out, not by the customer leaving. A cart not in checkout is returned as is: nothing is saved
 * or written.
 */
@Service
class AbandonCheckoutUseCase(
    private val cartRepository: CartRepository,
    private val outboxWriter: OutboxWriter,
    private val transactionTemplate: TransactionTemplate,
    private val checkoutSessionExpiry: CheckoutSessionExpiry
) {
    fun execute(command: AbandonCheckoutCommand, correlationId: UUID = UUID.randomUUID()): Either<CartError, Cart> =
        retryOnConflict("Abandon checkout") { abandonOnce(command, correlationId) }

    private fun abandonOnce(command: AbandonCheckoutCommand, correlationId: UUID): Either<CartError, Cart> {
        val now = Instant.now()
        val result = transactionTemplate.execute {
            val cart = cartRepository.findOwnedCart(command.owner, command.cartId)
                ?: return@execute CartError.CartNotFound(command.cartId).left()
            val lapsed = cart.lapsedCheckoutSession(now) != null
            val abandoned = cart.abandonCheckout(now)
            val saved = if (abandoned == null) cart else cartRepository.save(cart)
            when {
                abandoned == null -> Unit
                lapsed -> checkoutSessionExpiry.report(saved, abandoned, correlationId)
                else -> outboxWriter.write(checkoutAbandoned(saved, abandoned, correlationId))
            }
            saved.right()
        }
        return checkNotNull(result) { "transaction for cart ${command.cartId} returned no result" }
    }
}

/** `CheckoutAbandoned` for [abandoned], the session [cart] just left; merge writes it too (PIN-373). */
internal fun checkoutAbandoned(cart: Cart, abandoned: CheckoutSession, correlationId: UUID): CheckoutAbandoned =
    CheckoutAbandoned.create(
        CheckoutAbandonedPayload(
            cartId = cart.id,
            checkoutSessionId = abandoned.id,
            lineCount = cart.items.size,
            itemCount = cart.itemCount,
            sessionId = cart.sessionId,
            userId = cart.userId
        ),
        correlationId
    )
