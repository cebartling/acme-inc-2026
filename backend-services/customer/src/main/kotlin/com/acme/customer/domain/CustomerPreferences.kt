package com.acme.customer.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * JPA entity representing customer communication and privacy preferences.
 *
 * This entity stores the customer's preferences for notifications,
 * marketing communications, and data sharing. It has a one-to-one
 * relationship with the [Customer] entity.
 *
 * @property customerId The customer's UUID (primary key, foreign key to customers).
 * @property emailNotifications Whether to send email notifications.
 * @property smsNotifications Whether to send SMS notifications.
 * @property pushNotifications Whether to send push notifications.
 * @property marketingCommunications Whether customer opted in to marketing.
 * @property shareDataWithPartners Whether to share data with partners.
 * @property allowAnalytics Whether to allow analytics tracking.
 * @property updatedAt Timestamp when preferences were last modified.
 */
@Entity
@Table(name = "customer_preferences")
class CustomerPreferences(
    @Id
    @Column(name = "customer_id", nullable = false, updatable = false)
    val customerId: UUID,

    @Column(name = "email_notifications", nullable = false)
    var emailNotifications: Boolean = true,

    @Column(name = "sms_notifications", nullable = false)
    var smsNotifications: Boolean = false,

    @Column(name = "push_notifications", nullable = false)
    var pushNotifications: Boolean = false,

    @Column(name = "marketing_communications", nullable = false)
    var marketingCommunications: Boolean = false,

    @Column(name = "share_data_with_partners", nullable = false)
    var shareDataWithPartners: Boolean = false,

    @Column(name = "allow_analytics", nullable = false)
    var allowAnalytics: Boolean = true,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CustomerPreferences) return false
        return customerId == other.customerId
    }

    override fun hashCode(): Int = customerId.hashCode()

    companion object {
        /**
         * Creates default preferences for a new customer.
         *
         * @param customerId The customer's UUID.
         * @param marketingOptIn Whether customer opted in to marketing during registration.
         * @return A new [CustomerPreferences] instance with defaults applied.
         */
        fun createDefault(customerId: UUID, marketingOptIn: Boolean): CustomerPreferences {
            return CustomerPreferences(
                customerId = customerId,
                emailNotifications = true,
                smsNotifications = false,
                pushNotifications = false,
                marketingCommunications = marketingOptIn,
                shareDataWithPartners = false,
                allowAnalytics = true
            )
        }
    }
}
