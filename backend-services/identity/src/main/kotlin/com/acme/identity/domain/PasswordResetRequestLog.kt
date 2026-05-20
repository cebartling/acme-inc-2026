package com.acme.identity.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * JPA entity logging each password-reset request attempt for rate-limit
 * tracking. Rate limiting is enforced per-email regardless of whether
 * the email maps to a real account, so the email itself is recorded
 * here (not the user id).
 */
@Entity
@Table(name = "password_reset_request_logs")
class PasswordResetRequestLog(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "email", nullable = false)
    val email: String,

    @Column(name = "ip_address", length = 45)
    val ipAddress: String? = null,

    @Column(name = "requested_at", nullable = false, updatable = false)
    val requestedAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PasswordResetRequestLog) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
