package com.acme.cart.infrastructure.persistence

import com.acme.cart.domain.CartStatus
import java.time.Instant
import java.util.UUID

/** An EXPIRED, MERGED or CONVERTED cart past retention, as [CartRepository.findFinalCartsBefore] finds it (PIN-289). */
data class FinalCart(
    val id: UUID,
    val status: CartStatus,
    /** When the cart expired, merged or converted: its last `updated_at`. */
    val finalizedAt: Instant
)
