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

    data class PricingUnavailable(val variantId: UUID) : CartError {
        override val message = "Pricing is temporarily unavailable for variant $variantId"
    }
}
