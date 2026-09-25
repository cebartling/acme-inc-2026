package com.acme.cart.infrastructure.persistence

import com.acme.cart.domain.CartStatus
import java.time.Instant
import java.util.UUID

/** An EXPIRED or MERGED cart past retention, as [CartRepository.findFinalCartsBefore] finds it (PIN-289). */
data class FinalCart(
    val id: UUID,
    val status: CartStatus,
    /** Only guest carts become final today, but the schema does not require it. */
    val sessionId: String?,
    /** When the cart expired or merged: its last `updated_at`. */
    val finalizedAt: Instant
)
