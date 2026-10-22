package com.acme.cart.application

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartError
import com.acme.cart.domain.CartOwner
import com.acme.cart.domain.QuantityChange
import com.acme.cart.domain.events.CartItemQuantityUpdated
import com.acme.cart.domain.events.CartItemQuantityUpdatedPayload
import com.acme.cart.infrastructure.messaging.OutboxWriter
import com.acme.cart.infrastructure.persistence.CartRepository
import com.acme.cart.infrastructure.product.ProductPricingClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

data class UpdateCartItemQuantityCommand(
    val owner: CartOwner,
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
    private val outboxWriter: OutboxWriter,
    private val transactionTemplate: TransactionTemplate,
    @Value("\${acme.cart.max-order-quantity}") private val maxOrderQuantity: Int
) {
    fun execute(command: UpdateCartItemQuantityCommand, correlationId: UUID = UUID.randomUUID()): Either<CartError, Cart> {
        val variantId = cartRepository.findOwnedCart(command.owner, command.cartId)
            ?.items?.find { it.id == command.itemId }?.variantId
            ?: return CartError.CartItemNotFound(command.itemId).left()

        return pricingClient.getPricing(variantId).flatMap { pricing ->
            // A line's variant never changes, so neither does its price: only the transaction is
            // retried (PIN-278), and its read finds the line gone if a concurrent remove won.
            retryOnConflict("Update cart item quantity") {
                val result = transactionTemplate.execute {
                    val cart = cartRepository.findOwnedCart(command.owner, command.cartId)
                        ?: return@execute CartError.CartItemNotFound(command.itemId).left()
                    cart.updateItemQuantity(command.itemId, command.quantity, pricing, maxOrderQuantity)
                        .map { change ->
                            val saved = cartRepository.save(cart)
                            // A same-quantity request (e.g. the client's clamp retry at the max) is not a change.
                            if (change.previousQuantity != change.item.quantity) writeEvent(saved, change, correlationId)
                            saved
                        }
                }
                checkNotNull(result) { "transaction for cart ${command.cartId} returned no result" }
            }
        }
    }

    private fun writeEvent(cart: Cart, change: QuantityChange, correlationId: UUID) {
        outboxWriter.write(
            CartItemQuantityUpdated.create(
                CartItemQuantityUpdatedPayload(
                    cartId = cart.id,
                    cartItemId = change.item.id,
                    variantId = change.item.variantId,
                    previousQuantity = change.previousQuantity,
                    newQuantity = change.item.quantity,
                    reason = CartItemQuantityUpdated.REASON_CUSTOMER_UPDATE,
                    sessionId = cart.sessionId,
                    userId = cart.userId
                ),
                correlationId
            )
        )
    }
}
