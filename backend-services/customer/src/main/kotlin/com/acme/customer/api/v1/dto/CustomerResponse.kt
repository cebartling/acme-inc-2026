package com.acme.customer.api.v1.dto

import com.acme.customer.domain.Customer
import com.acme.customer.domain.CustomerPreferences
import com.acme.customer.domain.CustomerStatus
import com.acme.customer.domain.CustomerType
import java.time.Instant
import java.time.LocalDate

/**
 * Response DTO for customer profile data.
 */
data class CustomerResponse(
    val customerId: String,
    val userId: String,
    val customerNumber: String,
    val name: NameResponse,
    val email: EmailResponse,
    val phone: PhoneResponse?,
    val status: CustomerStatus,
    val type: CustomerType,
    val profile: ProfileResponse,
    val preferences: PreferencesResponse,
    val profileCompleteness: Int,
    val registeredAt: Instant,
    val lastActivityAt: Instant
) {
    companion object {
        fun fromDomain(customer: Customer, preferences: CustomerPreferences): CustomerResponse {
            return CustomerResponse(
                customerId = customer.id.toString(),
                userId = customer.userId.toString(),
                customerNumber = customer.customerNumber,
                name = NameResponse(
                    firstName = customer.firstName,
                    lastName = customer.lastName,
                    displayName = customer.displayName
                ),
                email = EmailResponse(
                    address = customer.email,
                    verified = customer.emailVerified
                ),
                phone = customer.phoneNumber?.let {
                    PhoneResponse(
                        countryCode = customer.phoneCountryCode,
                        number = it,
                        verified = customer.phoneVerified ?: false
                    )
                },
                status = customer.status,
                type = customer.type,
                profile = ProfileResponse(
                    dateOfBirth = customer.dateOfBirth,
                    gender = customer.gender,
                    preferredLocale = customer.preferredLocale,
                    timezone = customer.timezone,
                    preferredCurrency = customer.preferredCurrency
                ),
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
                profileCompleteness = customer.profileCompleteness,
                registeredAt = customer.registeredAt,
                lastActivityAt = customer.lastActivityAt
            )
        }
    }
}

data class NameResponse(
    val firstName: String,
    val lastName: String,
    val displayName: String
)

data class EmailResponse(
    val address: String,
    val verified: Boolean
)

data class PhoneResponse(
    val countryCode: String?,
    val number: String,
    val verified: Boolean
)

data class ProfileResponse(
    val dateOfBirth: LocalDate?,
    val gender: String?,
    val preferredLocale: String,
    val timezone: String,
    val preferredCurrency: String
)

data class PreferencesResponse(
    val communication: CommunicationPreferencesResponse,
    val privacy: PrivacyPreferencesResponse,
    val display: DisplayPreferencesResponse
)

data class CommunicationPreferencesResponse(
    val email: Boolean,
    val sms: Boolean,
    val push: Boolean,
    val marketing: Boolean,
    val frequency: String
)

data class PrivacyPreferencesResponse(
    val shareDataWithPartners: Boolean,
    val allowAnalytics: Boolean,
    val allowPersonalization: Boolean
)

data class DisplayPreferencesResponse(
    val language: String,
    val currency: String,
    val timezone: String
)
