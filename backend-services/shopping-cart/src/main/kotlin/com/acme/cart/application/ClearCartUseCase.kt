package com.acme.cart.application

import arrow.core.Either
import arrow.core.left
import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartError
import com.acme.cart.domain.CartItem
import com.acme.cart.domain.CartOwner
import com.acme.cart.domain.events.CartCleared
import com.acme.cart.domain.events.CartClearedPayload
import com.acme.cart.infrastructure.messaging.CartEventPublisher
import com.acme.cart.infrastructure.persistence.CartRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

data class ClearCartCommand(val owner: CartOwner, val cartId: UUID)

/**
 * Removes every line from the caller's own cart (PIN-294). The cart stays. Clearing an empty
 * cart is not a change: nothing is saved or published.
 */
@Service
class ClearCartUseCase(
    private val cartRepository: CartRepository,
    private val eventPublisher: CartEventPublisher,
    private val transactionTemplate: TransactionTemplate
) {
    private val logger = LoggerFactory.getLogger(ClearCartUseCase::class.java)

    fun execute(command: ClearCartCommand, correlationId: UUID = UUID.randomUUID()): Either<CartError, Cart> =
        retryOnConflict("Clear cart") { clearOnce(command, correlationId) }

    private fun clearOnce(command: ClearCartCommand, correlationId: UUID): Either<CartError, Cart> {
        val result = transactionTemplate.execute {
            val cart = cartRepository.findOwnedCart(command.owner, command.cartId)
                ?: return@execute CartError.CartNotFound(command.cartId).left()
            cart.clear().map { removed ->
                val saved = if (removed.isEmpty()) cart else cartRepository.save(cart)
                saved to removed
            }
        }
        return checkNotNull(result) { "transaction for cart ${command.cartId} returned no result" }
            .map { (cart, removed) ->
                if (removed.isNotEmpty()) publish(cart, removed, correlationId)
                cart
            }
    }

    private fun publish(cart: Cart, removed: List<CartItem>, correlationId: UUID) {
        eventPublisher.publishLoggingFailure(
            CartCleared.create(
                CartClearedPayload(
                    cartId = cart.id,
                    lineCount = removed.size,
                    itemCount = removed.sumOf { it.quantity },
                    sessionId = cart.sessionId,
                    userId = cart.userId
                ),
                correlationId
            ),
            logger
        )
    }
}
