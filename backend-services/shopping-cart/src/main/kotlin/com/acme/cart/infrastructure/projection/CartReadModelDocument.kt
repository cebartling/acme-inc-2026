package com.acme.cart.infrastructure.projection

/**
 * Layout of a cart read model document in MongoDB, shared by the projector (writes) and the
 * query side (reads).
 *
 * ```
 * { _id: cartId, userId, sessionId, status,          // the lookups `GET /carts/current` uses
 *   cartJson: "<the CartResponse body>",              // returned as is
 *   appliedEventIds: [ ...the last MAX_APPLIED_EVENT_IDS source events ] }
 * ```
 */
object CartReadModelDocument {
    const val COLLECTION = "cart_read_models"

    const val ID = "_id"
    const val USER_ID = "userId"
    const val SESSION_ID = "sessionId"
    const val STATUS = "status"
    const val CART_JSON = "cartJson"
    const val APPLIED_EVENT_IDS = "appliedEventIds"

    /** Enough for a reader to find its write's event even if later writes have landed. */
    const val MAX_APPLIED_EVENT_IDS = 50
}
