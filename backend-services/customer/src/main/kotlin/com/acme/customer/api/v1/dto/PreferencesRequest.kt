package com.acme.customer.api.v1.dto

import com.acme.customer.domain.NotificationFrequency

/**
 * Request DTO for updating customer preferences.
 *
 * All fields are optional to support partial updates.
 * Only fields present in the request will be updated.
 */
data class UpdatePreferencesRequest(
    val communication: CommunicationPreferencesRequest? = null,
    val privacy: PrivacyPreferencesRequest? = null,
    val display: DisplayPreferencesRequest? = null
) {
    /**
     * Checks if the request contains any updates.
     */
    fun hasUpdates(): Boolean {
        return communication?.hasUpdates() == true ||
               privacy?.hasUpdates() == true ||
               display?.hasUpdates() == true
    }
}

/**
 * Communication preferences section of the update request.
 */
data class CommunicationPreferencesRequest(
    val email: Boolean? = null,
    val sms: Boolean? = null,
    val push: Boolean? = null,
    val marketing: Boolean? = null,
    val frequency: String? = null
) {
    /**
     * Checks if this section contains any updates.
     */
    fun hasUpdates(): Boolean {
        return email != null || sms != null || push != null ||
               marketing != null || frequency != null
    }

    /**
     * Parses the frequency string to NotificationFrequency enum.
     *
     * @return The parsed NotificationFrequency or null if invalid/not provided.
     */
    fun getNotificationFrequency(): NotificationFrequency? {
        return frequency?.let { NotificationFrequency.fromString(it) }
    }
}

/**
 * Privacy preferences section of the update request.
 */
data class PrivacyPreferencesRequest(
    val shareDataWithPartners: Boolean? = null,
    val allowAnalytics: Boolean? = null,
    val allowPersonalization: Boolean? = null
) {
    /**
     * Checks if this section contains any updates.
     */
    fun hasUpdates(): Boolean {
        return shareDataWithPartners != null || allowAnalytics != null ||
               allowPersonalization != null
    }
}

/**
 * Display preferences section of the update request.
 */
data class DisplayPreferencesRequest(
    val language: String? = null,
    val currency: String? = null,
    val timezone: String? = null
) {
    /**
     * Checks if this section contains any updates.
     */
    fun hasUpdates(): Boolean {
        return language != null || currency != null || timezone != null
    }
}
