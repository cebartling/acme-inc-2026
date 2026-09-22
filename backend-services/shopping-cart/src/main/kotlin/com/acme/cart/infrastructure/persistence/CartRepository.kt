package com.acme.cart.infrastructure.persistence

import com.acme.cart.domain.Cart
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CartRepository : JpaRepository<Cart, UUID> {
    /** Loads the lines with the cart: every caller reads or changes them. */
    @EntityGraph(attributePaths = ["items"])
    fun findBySessionId(sessionId: String): Cart?
}
