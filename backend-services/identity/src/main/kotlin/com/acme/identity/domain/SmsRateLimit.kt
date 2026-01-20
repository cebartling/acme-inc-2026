package com.acme.identity.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for tracking SMS sending history for rate limiting.
 *
 * Records each SMS sent to enable sliding window rate limiting
 * (e.g., max 3 SMS per hour per user).
 *
 * @property id Unique identifier for the record.
 * @property userId The ID of the user this SMS was sent to.
 * @property sentAt Timestamp when the SMS was sent.
 * @property phoneNumber The phone number the SMS was sent to (for audit).
 */
@Entity
@Table(name = "sms_rate_limits")
class SmsRateLimit(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "sent_at", nullable = false)
    val sentAt: Instant,

    @Column(name = "phone_number", nullable = false, length = 20)
    val phoneNumber: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SmsRateLimit) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    companion object {
        /**
         * Creates a new SMS rate limit record.
         *
         * @param userId The ID of the user.
         * @param phoneNumber The phone number the SMS was sent to.
         * @return A new SmsRateLimit instance.
         */
        fun create(userId: UUID, phoneNumber: String): SmsRateLimit {
            return SmsRateLimit(
                id = UUID.randomUUID(),
                userId = userId,
                sentAt = Instant.now(),
                phoneNumber = phoneNumber
            )
        }
    }
}
