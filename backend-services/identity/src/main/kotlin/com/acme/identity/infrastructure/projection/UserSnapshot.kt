package com.acme.identity.infrastructure.projection

import com.acme.identity.domain.User
import java.time.Instant
import java.util.UUID

/**
 * A user's state as of one moment, for the MongoDB users read model (PIN-548, ADR-0067).
 *
 * It holds only what an admin list or lookup would show. It leaves out everything secret or
 * sensitive: the password hash, the TOTP secret, the phone number and device fingerprints.
 */
data class UserSnapshot(
    val userId: UUID,
    val email: String,
    val firstName: String,
    val lastName: String,
    val status: String,
    val emailVerified: Boolean,
    val verifiedAt: Instant?,
    val lockedUntil: Instant?,
    val registrationSource: String,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun fromDomain(user: User) = UserSnapshot(
            userId = user.id,
            email = user.email,
            firstName = user.firstName,
            lastName = user.lastName,
            status = user.status.name,
            emailVerified = user.emailVerified,
            verifiedAt = user.verifiedAt,
            lockedUntil = user.lockedUntil,
            registrationSource = user.registrationSource.name,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt
        )
    }
}

/**
 * The outbox payload for `identity.read-model`.
 *
 * [sourceEventId] is the `identity.*` event that caused this snapshot.
 */
data class UserSnapshotMessage(
    val sourceEventId: UUID,
    val userId: UUID,
    val snapshot: UserSnapshot
) {
    companion object {
        const val TOPIC = "identity.read-model"
        const val EVENT_TYPE = "UserSnapshot"
    }
}
