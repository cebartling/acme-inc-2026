package com.acme.identity.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Single-use token issued when a customer requests a password reset.
 * Mirrors [ReactivationToken] but has its own TTL (1 hour) and audit
 * trail so the reset flow can be reasoned about independently.
 */
@Entity
@Table(name = "password_reset_tokens")
class PasswordResetToken(
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
    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)

    fun isUsed(): Boolean = usedAt != null

    fun isValid(): Boolean = !isExpired() && !isUsed()

    fun markAsUsed() {
        usedAt = Instant.now()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PasswordResetToken) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
