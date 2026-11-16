package com.acme.cart.infrastructure.projection

import com.acme.cart.api.v1.CartResponse
import java.util.UUID

/**
 * A cart's full state after a change, sent to the read model's projector (PIN-551).
 *
 * [cart] is the `GET /carts/current` response body, so a read is deserialization with no mapping.
 * It is null when the cart no longer exists (purged), which tells the projector to delete it.
 * [sourceEventId] names the event the snapshot follows, for read-your-write.
 */
data class CartSnapshotMessage(
    val sourceEventId: UUID,
    val cartId: UUID,
    val userId: UUID?,
    val sessionId: String?,
    val cart: CartResponse?
) {
    companion object {
        const val TOPIC = "cart.read-model"
        const val EVENT_TYPE = "CartSnapshot"
    }
}
