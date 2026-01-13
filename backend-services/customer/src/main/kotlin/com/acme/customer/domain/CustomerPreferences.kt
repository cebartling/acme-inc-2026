package com.acme.customer.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * JPA entity representing customer communication, privacy, and display preferences.
 *
 * This entity stores the customer's preferences for notifications,
 * marketing communications, privacy settings, and display options.
 * It has a one-to-one relationship with the [Customer] entity.
 *
 * @property customerId The customer's UUID (primary key, foreign key to customers).
 * @property emailNotifications Whether to send email notifications.
 * @property smsNotifications Whether to send SMS notifications.
 * @property pushNotifications Whether to send push notifications.
 * @property marketingCommunications Whether customer opted in to marketing.
 * @property notificationFrequency How often to batch notifications.
 * @property shareDataWithPartners Whether to share data with partners.
 * @property allowAnalytics Whether to allow analytics tracking.
 * @property allowPersonalization Whether to allow personalized recommendations.
 * @property language Preferred display language (e.g., en-US).
 * @property currency Preferred currency for prices (e.g., USD).
 * @property timezone Preferred timezone (e.g., America/New_York).
 * @property updatedAt Timestamp when preferences were last modified.
 */
@Entity
@Table(name = "customer_preferences")
class CustomerPreferences(
    @Id
    @Column(name = "customer_id", nullable = false, updatable = false)
    val customerId: UUID,

    // Communication preferences
    @Column(name = "email_notifications", nullable = false)
    var emailNotifications: Boolean = true,

    @Column(name = "sms_notifications", nullable = false)
    var smsNotifications: Boolean = false,

    @Column(name = "push_notifications", nullable = false)
    var pushNotifications: Boolean = false,

    @Column(name = "marketing_communications", nullable = false)
    var marketingCommunications: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_frequency", nullable = false)
    var notificationFrequency: NotificationFrequency = NotificationFrequency.IMMEDIATE,

    // Privacy preferences
    @Column(name = "share_data_with_partners", nullable = false)
    var shareDataWithPartners: Boolean = false,

    @Column(name = "allow_analytics", nullable = false)
    var allowAnalytics: Boolean = true,

    @Column(name = "allow_personalization", nullable = false)
    var allowPersonalization: Boolean = true,

    // Display preferences
    @Column(name = "language", nullable = false)
    var language: String = "en-US",

    @Column(name = "currency", nullable = false)
    var currency: String = "USD",

    @Column(name = "timezone", nullable = false)
    var timezone: String = "UTC",

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    /**
     * Detects changes between this preferences instance and the update request.
     *
     * @param emailNotifications New email notification preference (null = no change).
     * @param smsNotifications New SMS notification preference (null = no change).
     * @param pushNotifications New push notification preference (null = no change).
     * @param marketingCommunications New marketing preference (null = no change).
     * @param notificationFrequency New notification frequency (null = no change).
     * @param shareDataWithPartners New data sharing preference (null = no change).
     * @param allowAnalytics New analytics preference (null = no change).
     * @param allowPersonalization New personalization preference (null = no change).
     * @param language New language preference (null = no change).
     * @param currency New currency preference (null = no change).
     * @param timezone New timezone preference (null = no change).
     * @return Map of changed preference names to their old and new values.
     */
    fun detectChanges(
        emailNotifications: Boolean? = null,
        smsNotifications: Boolean? = null,
        pushNotifications: Boolean? = null,
        marketingCommunications: Boolean? = null,
        notificationFrequency: NotificationFrequency? = null,
        shareDataWithPartners: Boolean? = null,
        allowAnalytics: Boolean? = null,
        allowPersonalization: Boolean? = null,
        language: String? = null,
        currency: String? = null,
        timezone: String? = null
    ): Map<String, PreferenceChange> {
        val changes = mutableMapOf<String, PreferenceChange>()

        emailNotifications?.let {
            if (it != this.emailNotifications) {
                changes["communication.email"] = PreferenceChange(this.emailNotifications.toString(), it.toString())
            }
        }

        smsNotifications?.let {
            if (it != this.smsNotifications) {
                changes["communication.sms"] = PreferenceChange(this.smsNotifications.toString(), it.toString())
            }
        }

        pushNotifications?.let {
            if (it != this.pushNotifications) {
                changes["communication.push"] = PreferenceChange(this.pushNotifications.toString(), it.toString())
            }
        }

        marketingCommunications?.let {
            if (it != this.marketingCommunications) {
                changes["communication.marketing"] = PreferenceChange(this.marketingCommunications.toString(), it.toString())
            }
        }

        notificationFrequency?.let {
            if (it != this.notificationFrequency) {
                changes["communication.frequency"] = PreferenceChange(this.notificationFrequency.name, it.name)
            }
        }

        shareDataWithPartners?.let {
            if (it != this.shareDataWithPartners) {
                changes["privacy.shareDataWithPartners"] = PreferenceChange(this.shareDataWithPartners.toString(), it.toString())
            }
        }

        allowAnalytics?.let {
            if (it != this.allowAnalytics) {
                changes["privacy.allowAnalytics"] = PreferenceChange(this.allowAnalytics.toString(), it.toString())
            }
        }

        allowPersonalization?.let {
            if (it != this.allowPersonalization) {
                changes["privacy.allowPersonalization"] = PreferenceChange(this.allowPersonalization.toString(), it.toString())
            }
        }

        language?.let {
            if (it != this.language) {
                changes["display.language"] = PreferenceChange(this.language, it)
            }
        }

        currency?.let {
            if (it != this.currency) {
                changes["display.currency"] = PreferenceChange(this.currency, it)
            }
        }

        timezone?.let {
            if (it != this.timezone) {
                changes["display.timezone"] = PreferenceChange(this.timezone, it)
            }
        }

        return changes
    }

    /**
     * Applies changes to this preferences instance.
     *
     * @param emailNotifications New email notification preference (null = no change).
     * @param smsNotifications New SMS notification preference (null = no change).
     * @param pushNotifications New push notification preference (null = no change).
     * @param marketingCommunications New marketing preference (null = no change).
     * @param notificationFrequency New notification frequency (null = no change).
     * @param shareDataWithPartners New data sharing preference (null = no change).
     * @param allowAnalytics New analytics preference (null = no change).
     * @param allowPersonalization New personalization preference (null = no change).
     * @param language New language preference (null = no change).
     * @param currency New currency preference (null = no change).
     * @param timezone New timezone preference (null = no change).
     */
    fun applyChanges(
        emailNotifications: Boolean? = null,
        smsNotifications: Boolean? = null,
        pushNotifications: Boolean? = null,
        marketingCommunications: Boolean? = null,
        notificationFrequency: NotificationFrequency? = null,
        shareDataWithPartners: Boolean? = null,
        allowAnalytics: Boolean? = null,
        allowPersonalization: Boolean? = null,
        language: String? = null,
        currency: String? = null,
        timezone: String? = null
    ) {
        emailNotifications?.let { this.emailNotifications = it }
        smsNotifications?.let { this.smsNotifications = it }
        pushNotifications?.let { this.pushNotifications = it }
        marketingCommunications?.let { this.marketingCommunications = it }
        notificationFrequency?.let { this.notificationFrequency = it }
        shareDataWithPartners?.let { this.shareDataWithPartners = it }
        allowAnalytics?.let { this.allowAnalytics = it }
        allowPersonalization?.let { this.allowPersonalization = it }
        language?.let { this.language = it }
        currency?.let { this.currency = it }
        timezone?.let { this.timezone = it }
        this.updatedAt = Instant.now()
    }

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
                notificationFrequency = NotificationFrequency.IMMEDIATE,
                shareDataWithPartners = false,
                allowAnalytics = true,
                allowPersonalization = true,
                language = "en-US",
                currency = "USD",
                timezone = "UTC"
            )
        }
    }
}

/**
 * Represents a change to a preference value.
 *
 * @property oldValue The previous value (may be null for new preferences).
 * @property newValue The new value.
 */
data class PreferenceChange(
    val oldValue: String?,
    val newValue: String
)
