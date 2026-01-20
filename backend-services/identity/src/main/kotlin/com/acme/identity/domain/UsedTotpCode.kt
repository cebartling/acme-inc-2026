package com.acme.identity.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * JPA entity representing a used TOTP code.
 *
 * TOTP codes should only be valid for a single use to prevent replay attacks.
 * This entity tracks codes that have been used within their validity window.
 *
 * @property id Unique identifier for this record.
 * @property userId The ID of the user who used the code.
 * @property codeHash SHA-256 hash of the used code (we don't store the actual code).
 * @property timeStep The TOTP time step when the code was used.
 * @property usedAt Timestamp when the code was used.
 * @property expiresAt Timestamp when this record can be cleaned up.
 */
@Entity
@Table(
    name = "used_totp_codes",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["user_id", "code_hash", "time_step"])
    ]
)
class UsedTotpCode(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "code_hash", nullable = false, length = 64)
    val codeHash: String,

    @Column(name = "time_step", nullable = false)
    val timeStep: Long,

    @Column(name = "used_at", nullable = false)
    val usedAt: Instant = Instant.now(),

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UsedTotpCode) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    companion object {
        /** Default expiration: 2 minutes (covers TOTP window + tolerance). */
        const val DEFAULT_EXPIRY_SECONDS = 120L

        /**
         * Creates a new used TOTP code record.
         *
         * @param userId The ID of the user.
         * @param codeHash SHA-256 hash of the code.
         * @param timeStep The TOTP time step.
         * @param expirySeconds Time until this record expires (default: 120).
         * @return A new UsedTotpCode instance.
         */
        fun create(
            userId: UUID,
            codeHash: String,
            timeStep: Long,
            expirySeconds: Long = DEFAULT_EXPIRY_SECONDS
        ): UsedTotpCode {
            return UsedTotpCode(
                id = UUID.randomUUID(),
                userId = userId,
                codeHash = codeHash,
                timeStep = timeStep,
                usedAt = Instant.now(),
                expiresAt = Instant.now().plusSeconds(expirySeconds)
            )
        }
    }
}
