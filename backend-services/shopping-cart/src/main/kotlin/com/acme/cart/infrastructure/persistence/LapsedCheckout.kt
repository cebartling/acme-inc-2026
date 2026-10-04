package com.acme.cart.infrastructure.persistence

import java.time.Instant
import java.util.UUID

/** A CHECKOUT cart whose session has lapsed, as [CartRepository.findLapsedCheckouts] finds it (PIN-330). */
data class LapsedCheckout(
    val id: UUID,
    val checkoutSessionId: UUID,
    val expiredAt: Instant,
    val sessionId: String?,
    val userId: UUID?,
    /** Lines in the cart, not units. */
    val lineCount: Int
)
