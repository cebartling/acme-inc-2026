package com.acme.cart.domain

import java.util.UUID

/**
 * Reasons a cart operation can be refused, returned on the left of an Arrow `Either`.
 */
sealed interface CartError {
    val message: String

    data class MaxQuantityExceeded(val maxQuantity: Int) : CartError {
        override val message = "Maximum order quantity is $maxQuantity for this item"
    }

    data class VariantNotFound(val variantId: UUID) : CartError {
        override val message = "Variant not found: $variantId"
    }

    /** Also returned for an item in someone else's cart, so ownership is never revealed. */
    data class CartItemNotFound(val itemId: UUID) : CartError {
        override val message = "Cart item not found: $itemId"
    }

    /** Also returned for someone else's cart, so ownership is never revealed. */
    data class CartNotFound(val cartId: UUID) : CartError {
        override val message = "Cart not found: $cartId"
    }

    data class PricingUnavailable(val variantId: UUID) : CartError {
        override val message = "Pricing is temporarily unavailable for variant $variantId"
    }

    /** The cart is in checkout (PIN-329) and refuses every change until checkout ends. */
    data class CartLocked(val cartId: UUID) : CartError {
        override val message = "Cart $cartId is locked for checkout"
    }

    data class CartEmpty(val cartId: UUID) : CartError {
        override val message = "Cart $cartId is empty"
    }

    /** Checkout found lines that can't be ordered; the cart is not locked. */
    data class CartUnavailableItems(val lines: List<UnavailableLine>) : CartError {
        override val message = "Some items in your cart are no longer available"
    }

    data class AvailabilityUnavailable(val variantId: UUID) : CartError {
        override val message = "Availability is temporarily unavailable for variant $variantId"
    }
}

/** A cart line that checkout refused (PIN-329), and why. */
data class UnavailableLine(
    val cartItemId: UUID,
    val variantId: UUID,
    val productName: String,
    val issue: AvailabilityIssue
)

enum class AvailabilityIssue {
    /** The variant exists but is out of stock. */
    OUT_OF_STOCK,

    /** The variant is no longer found, e.g. its product was archived (PIN-306). */
    NOT_AVAILABLE
}
