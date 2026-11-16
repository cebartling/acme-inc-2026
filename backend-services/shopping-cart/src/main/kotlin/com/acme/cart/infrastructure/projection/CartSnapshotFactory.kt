package com.acme.cart.infrastructure.projection

import com.acme.cart.api.v1.CartResponse
import com.acme.cart.domain.ProductSnapshot
import com.acme.cart.infrastructure.persistence.CartRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Builds a cart's snapshot from its current state. It reads through the caller's transaction,
 * so the snapshot includes the change that is about to commit.
 */
@Component
class CartSnapshotFactory(
    private val cartRepository: CartRepository,
    private val objectMapper: ObjectMapper
) {
    fun build(cartId: UUID, sourceEventId: UUID): CartSnapshotMessage {
        val cart = cartRepository.findById(cartId).orElse(null)
            ?: return CartSnapshotMessage(sourceEventId, cartId, userId = null, sessionId = null, cart = null)
        return CartSnapshotMessage(
            sourceEventId = sourceEventId,
            cartId = cartId,
            userId = cart.userId,
            sessionId = cart.sessionId,
            cart = CartResponse.from(cart) { objectMapper.readValue(it, ProductSnapshot::class.java) }
        )
    }
}
