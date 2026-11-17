package com.acme.identity.infrastructure.projection

/**
 * Layout of a users read model document in MongoDB (PIN-548, ADR-0067), shared by the projector
 * (writes) and anything that reads it.
 *
 * ```
 * { _id: userId, email,                                     // the lookups a reader uses
 *   snapshot: { userId, email, firstName, lastName, status, emailVerified, verifiedAt,
 *               lockedUntil, registrationSource, createdAt, updatedAt },
 *   appliedEventIds: [ ...the last MAX_APPLIED_EVENT_IDS source events ] }
 * ```
 */
object UserReadModelDocument {
    const val COLLECTION = "user_read_models"

    const val ID = "_id"
    const val EMAIL = "email"
    const val SNAPSHOT = "snapshot"
    const val APPLIED_EVENT_IDS = "appliedEventIds"

    /** Enough to find an event's snapshot even if later ones have landed. */
    const val MAX_APPLIED_EVENT_IDS = 50
}
