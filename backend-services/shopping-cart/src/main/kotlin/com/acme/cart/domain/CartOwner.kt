package com.acme.cart.domain

import java.util.UUID

/**
 * Who a cart belongs to (US-0004-08): a guest's browser session, or a signed-in user.
 * A signed-in caller's cart is keyed by the user ID from their verified access token, so
 * it follows them to any device (AC-0004-08-07).
 */
sealed interface CartOwner {
    data class Guest(val sessionId: String) : CartOwner

    data class Customer(val userId: UUID) : CartOwner
}
