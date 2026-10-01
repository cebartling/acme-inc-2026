package com.acme.cart.domain

/**
 * A cart's lifecycle. An owner's current cart is ACTIVE or CHECKOUT ([CURRENT]). Starting
 * checkout locks an ACTIVE cart as CHECKOUT, which refuses every change (PIN-329). A guest
 * cart becomes MERGED once its lines have moved into the signed-in user's cart (US-0004-08),
 * or EXPIRED once it has been idle past the guest TTL (PIN-287). Both are final.
 */
enum class CartStatus {
    ACTIVE,
    MERGED,
    EXPIRED,
    CHECKOUT;

    companion object {
        /** The statuses an owner's current cart can have; at most one such cart per owner. */
        val CURRENT = listOf(ACTIVE, CHECKOUT)
    }
}
