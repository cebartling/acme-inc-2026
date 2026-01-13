package com.acme.customer.api.v1.dto

import com.acme.customer.domain.CustomerPreferences

/**
 * Response DTO for customer preferences API endpoint.
 *
 * This includes the customerId and updatedAt fields for the standalone
 * preferences endpoint, unlike the embedded PreferencesResponse used
 * in CustomerResponse.
 */
data class CustomerPreferencesResponse(
    val customerId: String,
    val preferences: PreferencesResponse,
    val updatedAt: String
) {
    companion object {
        /**
         * Creates a response DTO from domain entity.
         */
        fun fromDomain(preferences: CustomerPreferences): CustomerPreferencesResponse {
            return CustomerPreferencesResponse(
                customerId = preferences.customerId.toString(),
                preferences = PreferencesResponse(
                    communication = CommunicationPreferencesResponse(
                        email = preferences.emailNotifications,
                        sms = preferences.smsNotifications,
                        push = preferences.pushNotifications,
                        marketing = preferences.marketingCommunications,
                        frequency = preferences.notificationFrequency.name
                    ),
                    privacy = PrivacyPreferencesResponse(
                        shareDataWithPartners = preferences.shareDataWithPartners,
                        allowAnalytics = preferences.allowAnalytics,
                        allowPersonalization = preferences.allowPersonalization
                    ),
                    display = DisplayPreferencesResponse(
                        language = preferences.language,
                        currency = preferences.currency,
                        timezone = preferences.timezone
                    )
                ),
                updatedAt = preferences.updatedAt.toString()
            )
        }
    }
}
