package com.acme.cart.application

import arrow.core.Either
import arrow.core.flatMap
import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartError
import com.acme.cart.domain.CartItem
import com.acme.cart.domain.ProductSnapshot
import com.acme.cart.domain.events.CartCreated
import com.acme.cart.domain.events.DomainEvent
import com.acme.cart.domain.events.ItemAddedToCart
import com.acme.cart.domain.events.ItemAddedToCartPayload
import com.acme.cart.domain.events.Money
import com.acme.cart.infrastructure.messaging.CartEventPublisher
import com.acme.cart.infrastructure.persistence.CartRepository
import com.acme.cart.infrastructure.product.ProductPricingClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

data class AddItemToCartCommand(
    val sessionId: String,
    val variantId: UUID,
    val quantity: Int,
    val productSnapshot: ProductSnapshot
)

/**
 * Adds a variant to the session's cart, creating the cart on first add (US-0004-06).
 *
 * The price comes from the product service, never from the request. Events are
 * published after the transaction commits, so a consumer never sees an event for a
 * cart change that rolled back. Publishing is best-effort (no outbox): a Kafka failure
 * is logged and the customer's add still succeeds.
 */
@Service
class AddItemToCartUseCase(
    private val cartRepository: CartRepository,
    private val pricingClient: ProductPricingClient,
    private val eventPublisher: CartEventPublisher,
    private val transactionTemplate: TransactionTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${acme.cart.max-order-quantity}") private val maxOrderQuantity: Int
) {
    private val logger = LoggerFactory.getLogger(AddItemToCartUseCase::class.java)

    fun execute(command: AddItemToCartCommand, correlationId: UUID = UUID.randomUUID()): Either<CartError, Cart> =
        pricingClient.getPricing(command.variantId).flatMap { pricing ->
            val result = transactionTemplate.execute {
                val existing = cartRepository.findBySessionId(command.sessionId)
                val cart = existing ?: Cart(id = UUID.randomUUID(), sessionId = command.sessionId)

                cart.addItem(
                    variantId = command.variantId,
                    quantity = command.quantity,
                    pricing = pricing,
                    productSnapshot = objectMapper.writeValueAsString(command.productSnapshot),
                    maxQuantity = maxOrderQuantity
                ).map { item ->
                    val saved = cartRepository.save(cart)
                    Added(saved, item, isNewCart = existing == null)
                }
            }
            checkNotNull(result) { "transaction for session ${command.sessionId} returned no result" }
        }.map { added ->
            publishEvents(added, command, correlationId)
            added.cart
        }

    private data class Added(val cart: Cart, val item: CartItem, val isNewCart: Boolean)

    private fun publishEvents(added: Added, command: AddItemToCartCommand, correlationId: UUID) {
        val cart = added.cart
        if (added.isNewCart) {
            publish(CartCreated.create(cart.id, cart.sessionId, cart.customerId, correlationId))
        }
        publish(
            ItemAddedToCart.create(
                ItemAddedToCartPayload(
                    cartId = cart.id,
                    cartItemId = added.item.id,
                    productId = command.productSnapshot.productId,
                    variantId = command.variantId,
                    sku = command.productSnapshot.sku,
                    productName = command.productSnapshot.name,
                    quantity = command.quantity,
                    unitPrice = Money(added.item.unitPrice, CURRENCY),
                    sessionId = cart.sessionId,
                    customerId = cart.customerId
                ),
                correlationId
            )
        )
    }

    private fun publish(event: DomainEvent) = eventPublisher.publishLoggingFailure(event, logger)

    companion object {
        /** Product prices carry no currency; the catalog is USD-only today. */
        const val CURRENCY = "USD"
    }
}
