package com.acme.cart.application

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.right
import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartError
import com.acme.cart.domain.CartOwner
import com.acme.cart.domain.MergeResult
import com.acme.cart.domain.VariantPricing
import com.acme.cart.domain.newCartFor
import com.acme.cart.domain.events.CartMerged
import com.acme.cart.domain.events.CartMergedPayload
import com.acme.cart.infrastructure.messaging.CartEventPublisher
import com.acme.cart.infrastructure.persistence.CartRepository
import com.acme.cart.infrastructure.product.ProductPricingClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

data class MergeCartsCommand(val userId: UUID, val guestSessionId: String?)

/**
 * The signed-in user's cart after a merge, and what the merge did. [result] is null when
 * there was nothing to merge; [cart] is null when the user also has no cart.
 */
data class MergeOutcome(val cart: Cart?, val result: MergeResult?)

/**
 * Merges a guest's cart into their account cart on sign-in (US-0004-08).
 *
 * A missing, empty or already MERGED guest cart is a no-op (AC-08, and re-merging is
 * idempotent): the user's cart comes back unchanged and nothing is published. Otherwise
 * the guest variants are priced first, outside the transaction, then both carts are
 * re-read, merged and saved together, and `CartMerged` is published after commit.
 */
@Service
class MergeCartsUseCase(
    private val cartRepository: CartRepository,
    private val pricingClient: ProductPricingClient,
    private val eventPublisher: CartEventPublisher,
    private val transactionTemplate: TransactionTemplate,
    @Value("\${acme.cart.max-order-quantity}") private val maxOrderQuantity: Int
) {
    private val logger = LoggerFactory.getLogger(MergeCartsUseCase::class.java)

    fun execute(command: MergeCartsCommand, correlationId: UUID = UUID.randomUUID()): Either<CartError, MergeOutcome> {
        val customer = CartOwner.Customer(command.userId)
        val guestVariants = guestCart(command)?.items?.map { it.variantId }?.distinct()

        if (guestVariants.isNullOrEmpty()) {
            return MergeOutcome(cartRepository.findActiveCart(customer), result = null).right()
        }

        return either {
            val pricing = guestVariants.associateWith { pricingClient.getPricing(it).bind() }

            val outcome = checkNotNull(transactionTemplate.execute { mergeInTransaction(command, customer, pricing) }) {
                "merge transaction for user ${command.userId} returned no result"
            }
            if (outcome.merged != null) publish(outcome.merged, correlationId)
            outcome.outcome
        }
    }

    private fun guestCart(command: MergeCartsCommand): Cart? =
        command.guestSessionId?.let { cartRepository.findActiveCart(CartOwner.Guest(it)) }

    private fun mergeInTransaction(
        command: MergeCartsCommand,
        customer: CartOwner.Customer,
        pricing: Map<UUID, VariantPricing>
    ): TransactionOutcome {
        val userCart = cartRepository.findActiveCart(customer)
        // Re-read inside the transaction: a concurrent merge may have taken it already.
        val guest = guestCart(command)?.takeIf { it.items.isNotEmpty() }
            ?: return TransactionOutcome(MergeOutcome(userCart, result = null), merged = null)

        val target = userCart ?: newCartFor(customer)
        val result = target.absorb(guest, pricing, maxOrderQuantity)
        cartRepository.save(guest)
        val saved = cartRepository.save(target)
        return TransactionOutcome(MergeOutcome(saved, result), merged = Merged(saved, guest, result))
    }

    private data class TransactionOutcome(val outcome: MergeOutcome, val merged: Merged?)

    private data class Merged(val target: Cart, val source: Cart, val result: MergeResult)

    private fun publish(merged: Merged, correlationId: UUID) {
        eventPublisher.publishLoggingFailure(
            CartMerged.create(
                CartMergedPayload(
                    targetCartId = merged.target.id,
                    sourceCartId = merged.source.id,
                    userId = requireNotNull(merged.target.userId) { "merge target ${merged.target.id} has no user" },
                    itemsMerged = merged.result.itemsMerged,
                    quantitiesAdjusted = merged.result.quantitiesAdjusted.size
                ),
                correlationId
            ),
            logger
        )
    }
}
