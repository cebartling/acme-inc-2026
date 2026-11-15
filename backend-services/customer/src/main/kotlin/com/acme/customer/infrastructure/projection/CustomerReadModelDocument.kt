package com.acme.customer.infrastructure.projection

/**
 * Layout of a customer read model document in MongoDB, shared by the projector (writes) and the
 * query service (reads).
 *
 * ```
 * { _id: customerId, userId, customerNumber, email,        // the lookups the read endpoints use
 *   snapshot: { customer, addresses, consents, completeness },
 *   appliedEventIds: [ ...the last MAX_APPLIED_EVENT_IDS source events ] }
 * ```
 */
object CustomerReadModelDocument {
    const val COLLECTION = "customer_read_models"

    const val ID = "_id"
    const val USER_ID = "userId"
    const val CUSTOMER_NUMBER = "customerNumber"
    const val EMAIL = "email"
    const val SNAPSHOT = "snapshot"
    const val APPLIED_EVENT_IDS = "appliedEventIds"

    /** Enough for a reader to find its write's event even if later writes have landed. */
    const val MAX_APPLIED_EVENT_IDS = 50
}
