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
import com.acme.cart.infrastructure.messaging.CartEventPublisher
import com.acme.cart.infrastructure.persistence.CartRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

data class AbandonCheckoutCommand(val owner: CartOwner, val cartId: UUID)

/**
 * Leaves checkout on the caller's own cart (PIN-330): the cart is ACTIVE again with its lines,
 * and `CheckoutAbandoned` is published after commit. A cart not in checkout is returned as is:
 * nothing is saved or published.
 */
@Service
class AbandonCheckoutUseCase(
    private val cartRepository: CartRepository,
    private val eventPublisher: CartEventPublisher,
    private val transactionTemplate: TransactionTemplate
) {
    private val logger = LoggerFactory.getLogger(AbandonCheckoutUseCase::class.java)

    fun execute(command: AbandonCheckoutCommand, correlationId: UUID = UUID.randomUUID()): Either<CartError, Cart> =
        retryOnConflict("Abandon checkout") { abandonOnce(command, correlationId) }

    private fun abandonOnce(command: AbandonCheckoutCommand, correlationId: UUID): Either<CartError, Cart> {
        val result = transactionTemplate.execute {
            val cart = cartRepository.findOwnedCart(command.owner, command.cartId)
                ?: return@execute CartError.CartNotFound(command.cartId).left()
            val abandoned = cart.abandonCheckout()
            val saved = if (abandoned == null) cart else cartRepository.save(cart)
            (saved to abandoned).right()
        }
        return checkNotNull(result) { "transaction for cart ${command.cartId} returned no result" }
            .map { (cart, abandoned) ->
                if (abandoned != null) publish(cart, abandoned, correlationId)
                cart
            }
    }

    private fun publish(cart: Cart, abandoned: CheckoutSession, correlationId: UUID) {
        eventPublisher.publishLoggingFailure(
            CheckoutAbandoned.create(
                CheckoutAbandonedPayload(
                    cartId = cart.id,
                    checkoutSessionId = abandoned.id,
                    itemCount = cart.itemCount,
                    sessionId = cart.sessionId,
                    userId = cart.userId
                ),
                correlationId
            ),
            logger
        )
    }
}
