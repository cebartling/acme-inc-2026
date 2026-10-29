package com.acme.cart.application

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartError
import com.acme.cart.domain.CartOwner
import com.acme.cart.domain.CartStatus
import com.acme.cart.domain.MergeResult
import com.acme.cart.domain.VariantPricing
import com.acme.cart.domain.newCartFor
import com.acme.cart.domain.events.CartCreated
import com.acme.cart.domain.events.CartMerged
import com.acme.cart.domain.events.CartMergedPayload
import com.acme.cart.infrastructure.messaging.OutboxWriter
import com.acme.cart.infrastructure.persistence.CartRepository
import com.acme.cart.infrastructure.product.ProductPricingClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
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
 * A guest cart locked for checkout leaves checkout first (PIN-373). An account cart in
 * checkout refuses the merge, and then neither cart changes.
 *
 * A missing, empty or already MERGED guest cart is a no-op (AC-08, and re-merging is
 * idempotent): the user's cart comes back unchanged and nothing is written. Otherwise
 * the guest variants are priced first, outside the transaction, then both carts are
 * re-read (the guest cart row-locked), merged and saved together, and `CartMerged` (plus
 * `CartCreated` when the user had no cart) is written to the outbox in the same transaction.
 */
@Service
class MergeCartsUseCase(
    private val cartRepository: CartRepository,
    private val pricingClient: ProductPricingClient,
    private val outboxWriter: OutboxWriter,
    private val transactionTemplate: TransactionTemplate,
    private val checkoutSessionExpiry: CheckoutSessionExpiry,
    @Value("\${acme.cart.max-order-quantity}") private val maxOrderQuantity: Int
) {
    private val logger = LoggerFactory.getLogger(MergeCartsUseCase::class.java)

    fun execute(command: MergeCartsCommand, correlationId: UUID = UUID.randomUUID()): Either<CartError, MergeOutcome> =
        retryOnConflict("Merge carts") { mergeOnce(command, correlationId) }

    private fun mergeOnce(command: MergeCartsCommand, correlationId: UUID): Either<CartError, MergeOutcome> {
        val customer = CartOwner.Customer(command.userId)
        val guestVariants = guestCart(command)?.items?.map { it.variantId }?.distinct()

        if (guestVariants.isNullOrEmpty()) {
            return MergeOutcome(cartRepository.findActiveCart(customer), result = null).right()
        }

        return either {
            // A variant the product service no longer finds, e.g. an archived product's (PIN-306),
            // is left out rather than failing every sign-in; any other pricing error fails the
            // merge at once, without waiting on lookups for the remaining variants.
            val pricing = mutableMapOf<UUID, VariantPricing>()
            val unavailable = mutableSetOf<UUID>()
            for (variantId in guestVariants) {
                when (val lookup = pricingClient.getPricing(variantId)) {
                    is Either.Right -> pricing[variantId] = lookup.value
                    is Either.Left ->
                        if (lookup.value is CartError.VariantNotFound) unavailable += variantId else raise(lookup.value)
                }
            }
            if (unavailable.isNotEmpty()) {
                logger.info("Leaving guest variants no longer found out of user {}'s merge: {}", command.userId, unavailable)
            }

            checkNotNull(
                transactionTemplate.execute { mergeInTransaction(command, customer, pricing, unavailable, correlationId) }
            ) {
                "merge transaction for user ${command.userId} returned no result"
            }.bind()
        }
    }

    /** The guest's ACTIVE cart, or one locked for checkout, which the merge takes out of checkout (PIN-373). */
    private fun guestCart(command: MergeCartsCommand): Cart? =
        command.guestSessionId?.let { cartRepository.findActiveCart(CartOwner.Guest(it)) }

    private fun mergeInTransaction(
        command: MergeCartsCommand,
        customer: CartOwner.Customer,
        pricing: Map<UUID, VariantPricing>,
        unavailable: Set<UUID>,
        correlationId: UUID
    ): Either<CartError, MergeOutcome> {
        // Re-read and lock the guest cart first: a concurrent merge that took it already
        // has committed by the time the lock is granted, so this one sees no current cart.
        val guest = command.guestSessionId
            ?.let {
                cartRepository.findForUpdateBySessionIdAndStatus(it, CartStatus.ACTIVE)
                    ?: cartRepository.findForUpdateBySessionIdAndStatus(it, CartStatus.CHECKOUT)
            }
            ?.takeIf { it.items.isNotEmpty() }
        val userCart = cartRepository.findActiveCart(customer)
        if (guest == null) return MergeOutcome(userCart, result = null).right()
        // The account cart is in checkout (PIN-329): it takes no lines until checkout ends.
        if (userCart?.status == CartStatus.CHECKOUT) return CartError.CartLocked(userCart.id).left()

        // A line added to the guest cart after pricing has no price yet; the caller can retry.
        guest.items.firstOrNull { it.variantId !in pricing && it.variantId !in unavailable }
            ?.let { return CartError.PricingUnavailable(it.variantId).left() }

        leaveGuestCheckout(guest, correlationId)
        val target = userCart ?: newCartFor(customer)
        val result = target.absorb(guest, pricing, maxOrderQuantity, unavailable)
        cartRepository.save(guest)
        val saved = cartRepository.save(target)
        writeEvents(Merged(saved, guest, result, isNewCart = userCart == null), correlationId)
        return MergeOutcome(saved, result).right()
    }

    /**
     * The order service reads a checkout session with the signed-in customer's token only, so a
     * guest cart's session could never become an order now (PIN-373). The guest cart leaves
     * checkout as `AbandonCheckoutUseCase` does, and the customer starts checkout again on the
     * account cart. A guest cart not in checkout is left as it is.
     */
    private fun leaveGuestCheckout(guest: Cart, correlationId: UUID) {
        val now = Instant.now()
        val lapsed = guest.lapsedCheckoutSession(now) != null
        val left = guest.abandonCheckout(now) ?: return
        if (lapsed) checkoutSessionExpiry.report(guest, left, correlationId)
        else outboxWriter.write(checkoutAbandoned(guest, left, correlationId))
    }

    private data class Merged(val target: Cart, val source: Cart, val result: MergeResult, val isNewCart: Boolean)

    private fun writeEvents(merged: Merged, correlationId: UUID) {
        val target = merged.target
        if (merged.isNewCart) {
            outboxWriter.write(CartCreated.create(target.id, target.sessionId, target.userId, correlationId))
        }
        outboxWriter.write(
            CartMerged.create(
                CartMergedPayload(
                    targetCartId = merged.target.id,
                    sourceCartId = merged.source.id,
                    userId = requireNotNull(merged.target.userId) { "merge target ${merged.target.id} has no user" },
                    itemsMerged = merged.result.itemsMerged,
                    quantitiesAdjusted = merged.result.quantitiesAdjusted.size
                ),
                correlationId
            )
        )
    }
}
