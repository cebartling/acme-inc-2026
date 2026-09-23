package com.acme.cart.infrastructure.persistence

import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Lookups load the lines with the cart: every caller reads or changes them. */
interface CartRepository : JpaRepository<Cart, UUID> {
    @EntityGraph(attributePaths = ["items"])
    fun findBySessionIdAndStatus(sessionId: String, status: CartStatus): Cart?

    @EntityGraph(attributePaths = ["items"])
    fun findByUserIdAndStatus(userId: UUID, status: CartStatus): Cart?
}
