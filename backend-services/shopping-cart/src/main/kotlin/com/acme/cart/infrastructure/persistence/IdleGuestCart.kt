package com.acme.cart.infrastructure.persistence

import java.time.Instant
import java.util.UUID

/** An ACTIVE guest cart past the idle cutoff, as [CartRepository.findIdleGuestCarts] finds it (PIN-287). */
data class IdleGuestCart(
    val id: UUID,
    val sessionId: String,
    val lastActiveAt: Instant,
    /** Lines in the cart, not units. */
    val lineCount: Int
)
