package com.acme.cart.infrastructure.persistence

import com.acme.cart.domain.Cart
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CartRepository : JpaRepository<Cart, UUID> {
    fun findBySessionId(sessionId: String): Cart?
}
