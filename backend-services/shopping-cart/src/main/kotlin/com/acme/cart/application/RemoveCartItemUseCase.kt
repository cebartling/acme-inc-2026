package com.acme.cart.application

import arrow.core.Either
import arrow.core.left
import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartError
import com.acme.cart.domain.CartItem
import com.acme.cart.domain.events.CartItemRemoved
import com.acme.cart.domain.events.CartItemRemovedPayload
import com.acme.cart.infrastructure.messaging.CartEventPublisher
import com.acme.cart.infrastructure.persistence.CartRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

data class RemoveCartItemCommand(val sessionId: String, val cartId: UUID, val itemId: UUID)

/** Removes a line from the session's own cart (US-0004-07, AC-05). */
@Service
class RemoveCartItemUseCase(
    private val cartRepository: CartRepository,
    private val eventPublisher: CartEventPublisher,
    private val transactionTemplate: TransactionTemplate
) {
    private val logger = LoggerFactory.getLogger(RemoveCartItemUseCase::class.java)

    fun execute(command: RemoveCartItemCommand, correlationId: UUID = UUID.randomUUID()): Either<CartError, Cart> {
        val result = transactionTemplate.execute {
            val cart = cartRepository.findOwnedCart(command.sessionId, command.cartId)
                ?: return@execute CartError.CartItemNotFound(command.itemId).left()
            cart.removeItem(command.itemId).map { removed -> cartRepository.save(cart) to removed }
        }
        return checkNotNull(result) { "transaction for cart ${command.cartId} returned no result" }
            .map { (cart, removed) ->
                publish(cart, removed, correlationId)
                cart
            }
    }

    private fun publish(cart: Cart, removed: CartItem, correlationId: UUID) {
        eventPublisher.publishLoggingFailure(
            CartItemRemoved.create(
                CartItemRemovedPayload(
                    cartId = cart.id,
                    cartItemId = removed.id,
                    variantId = removed.variantId,
                    quantity = removed.quantity,
                    sessionId = cart.sessionId,
                    customerId = cart.customerId
                ),
                correlationId
            ),
            logger
        )
    }
}
