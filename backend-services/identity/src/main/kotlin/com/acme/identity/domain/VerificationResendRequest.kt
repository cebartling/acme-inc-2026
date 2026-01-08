package com.acme.identity.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * JPA entity representing a verification email resend request.
 *
 * This entity is used to track resend requests for rate limiting purposes.
 * Each request is recorded to enforce the maximum of 3 resend requests
 * per hour per email address.
 *
 * @property id Unique identifier for the request record.
 * @property email The email address that requested a resend.
 * @property ipAddress The IP address from which the request originated.
 * @property requestedAt Timestamp when the resend was requested.
 */
@Entity
@Table(name = "verification_resend_requests")
class VerificationResendRequest(
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
        if (other !is VerificationResendRequest) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
