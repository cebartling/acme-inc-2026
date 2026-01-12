package com.acme.customer.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for GDPR compliance audit logging of preference changes.
 *
 * This entity records every change made to customer preferences, including
 * the old and new values, timestamp, and client information (IP address, user agent).
 * This is required for GDPR compliance to maintain an audit trail.
 *
 * @property id Unique identifier for the log entry.
 * @property customerId The customer whose preferences were changed.
 * @property preferenceName Dot-notation path to the preference (e.g., communication.sms).
 * @property oldValue The previous value (null for new preferences).
 * @property newValue The new value.
 * @property changedAt Timestamp when the change was made.
 * @property ipAddress Client IP address when the change was made.
 * @property userAgent Client user agent string when the change was made.
 */
@Entity
@Table(name = "preference_change_log")
class PreferenceChangeLog(
    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "customer_id", nullable = false, updatable = false)
    val customerId: UUID,

    @Column(name = "preference_name", nullable = false, updatable = false, length = 50)
    val preferenceName: String,

    @Column(name = "old_value", updatable = false)
    val oldValue: String?,

    @Column(name = "new_value", nullable = false, updatable = false)
    val newValue: String,

    @Column(name = "changed_at", nullable = false, updatable = false)
    val changedAt: Instant = Instant.now(),

    @Column(name = "ip_address", length = 45)
    val ipAddress: String? = null,

    @Column(name = "user_agent")
    val userAgent: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PreferenceChangeLog) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    companion object {
        /**
         * Creates a new preference change log entry.
         *
         * @param customerId The customer whose preferences were changed.
         * @param preferenceName Dot-notation path to the preference.
         * @param oldValue The previous value (null for new preferences).
         * @param newValue The new value.
         * @param ipAddress Client IP address.
         * @param userAgent Client user agent string.
         * @return A new [PreferenceChangeLog] instance.
         */
        fun create(
            customerId: UUID,
            preferenceName: String,
            oldValue: String?,
            newValue: String,
            ipAddress: String? = null,
            userAgent: String? = null
        ): PreferenceChangeLog {
            return PreferenceChangeLog(
                id = UUID.randomUUID(),
                customerId = customerId,
                preferenceName = preferenceName,
                oldValue = oldValue,
                newValue = newValue,
                changedAt = Instant.now(),
                ipAddress = ipAddress,
                userAgent = userAgent
            )
        }
    }
}
