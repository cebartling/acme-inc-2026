package com.acme.cart.domain

/**
 * A cart's lifecycle. Only ACTIVE carts are ever resolved for an owner; a guest cart
 * becomes MERGED once its lines have moved into the signed-in user's cart (US-0004-08),
 * or EXPIRED once it has been idle past the guest TTL (PIN-287). Both are final.
 */
enum class CartStatus {
    ACTIVE,
    MERGED,
    EXPIRED
}
