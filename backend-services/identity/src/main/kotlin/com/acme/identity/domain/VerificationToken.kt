package com.acme.identity.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * JPA entity representing an email verification token.
 *
 * Verification tokens are generated during user registration and sent
 * via email to confirm the user's email address. Tokens are single-use
 * and expire after a configurable period (default 24 hours).
 *
 * @property id Unique identifier for the token record.
 * @property userId The ID of the user this token belongs to.
 * @property token The cryptographically secure random token string.
 * @property expiresAt Timestamp when this token expires.
 * @property usedAt Timestamp when the token was used, null if unused.
 * @property createdAt Timestamp when the token was created.
 */
@Entity
@Table(name = "verification_tokens")
class VerificationToken(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "token", nullable = false, unique = true)
    val token: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "used_at")
    var usedAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
) {
    /**
     * Checks if this token has expired.
     *
     * @return `true` if the current time is after the expiration time.
     */
    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)

    /**
     * Checks if this token has already been used.
     *
     * @return `true` if [usedAt] is not null.
     */
    fun isUsed(): Boolean = usedAt != null

    /**
     * Checks if this token is valid for use.
     *
     * A token is valid if it has not expired and has not been used.
     *
     * @return `true` if the token can be used for verification.
     */
    fun isValid(): Boolean = !isExpired() && !isUsed()

    /**
     * Marks this token as used by setting the [usedAt] timestamp.
     *
     * This should be called when the token is successfully used for
     * email verification to prevent reuse.
     */
    fun markAsUsed() {
        usedAt = Instant.now()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VerificationToken) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
