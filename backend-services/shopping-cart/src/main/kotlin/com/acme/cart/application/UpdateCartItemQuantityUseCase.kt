package com.acme.cart.application

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartError
import com.acme.cart.domain.QuantityChange
import com.acme.cart.domain.events.CartItemQuantityUpdated
import com.acme.cart.domain.events.CartItemQuantityUpdatedPayload
import com.acme.cart.infrastructure.messaging.CartEventPublisher
import com.acme.cart.infrastructure.persistence.CartRepository
import com.acme.cart.infrastructure.product.ProductPricingClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

data class UpdateCartItemQuantityCommand(
    val sessionId: String,
    val cartId: UUID,
    val itemId: UUID,
    val quantity: Int
)

/**
 * Changes a line's quantity in the session's own cart (US-0004-07, AC-04 and AC-08).
 *
 * The line's variant is read first so its price can be fetched outside the transaction;
 * the change itself is applied to a fresh read inside it.
 */
@Service
class UpdateCartItemQuantityUseCase(
    private val cartRepository: CartRepository,
    private val pricingClient: ProductPricingClient,
    private val eventPublisher: CartEventPublisher,
    private val transactionTemplate: TransactionTemplate,
    @Value("\${acme.cart.max-order-quantity}") private val maxOrderQuantity: Int
) {
    private val logger = LoggerFactory.getLogger(UpdateCartItemQuantityUseCase::class.java)

    fun execute(command: UpdateCartItemQuantityCommand, correlationId: UUID = UUID.randomUUID()): Either<CartError, Cart> {
        val variantId = cartRepository.findOwnedCart(command.sessionId, command.cartId)
            ?.items?.find { it.id == command.itemId }?.variantId
            ?: return CartError.CartItemNotFound(command.itemId).left()

        return pricingClient.getPricing(variantId).flatMap { pricing ->
            val result = transactionTemplate.execute {
                val cart = cartRepository.findOwnedCart(command.sessionId, command.cartId)
                    ?: return@execute CartError.CartItemNotFound(command.itemId).left()
                cart.updateItemQuantity(command.itemId, command.quantity, pricing, maxOrderQuantity)
                    .map { change -> cartRepository.save(cart) to change }
            }
            checkNotNull(result) { "transaction for cart ${command.cartId} returned no result" }
        }.map { (cart, change) ->
            // A same-quantity request (e.g. the client's clamp retry at the max) is not a change.
            if (change.previousQuantity != change.item.quantity) {
                publish(cart, change, correlationId)
            }
            cart
        }
    }

    private fun publish(cart: Cart, change: QuantityChange, correlationId: UUID) {
        eventPublisher.publishLoggingFailure(
            CartItemQuantityUpdated.create(
                CartItemQuantityUpdatedPayload(
                    cartId = cart.id,
                    cartItemId = change.item.id,
                    variantId = change.item.variantId,
                    previousQuantity = change.previousQuantity,
                    newQuantity = change.item.quantity,
                    reason = CartItemQuantityUpdated.REASON_CUSTOMER_UPDATE,
                    sessionId = cart.sessionId,
                    customerId = cart.customerId
                ),
                correlationId
            ),
            logger
        )
    }
}
