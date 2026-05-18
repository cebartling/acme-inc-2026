package com.acme.identity.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Single-use token issued when a DEACTIVATED customer requests
 * reactivation. Kept separate from [VerificationToken] so the reactivation
 * flow has its own TTL and audit trail.
 */
@Entity
@Table(name = "reactivation_tokens")
class ReactivationToken(
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
        if (other !is ReactivationToken) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
