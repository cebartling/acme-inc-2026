package com.acme.customer.api.v1.dto

import java.time.LocalDate

/**
 * Phone number data for profile updates.
 *
 * @property countryCode The country code (e.g., "+1").
 * @property number The phone number without country code.
 */
data class PhoneRequest(
    val countryCode: String,
    val number: String
)

/**
 * Request DTO for updating a customer's profile.
 *
 * All fields are optional to support partial updates (PATCH semantics).
 * Only fields that are present in the request will be updated.
 *
 * @property phone Phone number details (optional).
 * @property dateOfBirth Date of birth (optional).
 * @property gender Gender (optional). Allowed values: MALE, FEMALE, NON_BINARY, PREFER_NOT_TO_SAY.
 * @property preferredLocale Preferred locale (optional). Example: "en-US".
 * @property timezone Timezone (optional). Example: "America/New_York".
 */
data class UpdateProfileRequest(
    val phone: PhoneRequest? = null,
    val dateOfBirth: LocalDate? = null,
    val gender: String? = null,
    val preferredLocale: String? = null,
    val timezone: String? = null
) {
    /**
     * Returns true if at least one field is present for update.
     */
    fun hasUpdates(): Boolean {
        return phone != null ||
            dateOfBirth != null ||
            gender != null ||
            preferredLocale != null ||
            timezone != null
    }

    /**
     * Returns the list of field names that are present in the request.
     */
    fun getPresentFields(): List<String> {
        return buildList {
            if (phone != null) add("phone")
            if (dateOfBirth != null) add("dateOfBirth")
            if (gender != null) add("gender")
            if (preferredLocale != null) add("preferredLocale")
            if (timezone != null) add("timezone")
        }
    }
}

/**
 * Response DTO for profile update operations.
 *
 * @property customerId The customer's unique identifier.
 * @property profile The updated profile details.
 * @property profileCompleteness The updated profile completeness percentage.
 * @property updatedAt When the profile was last updated.
 */
data class UpdateProfileResponse(
    val customerId: String,
    val profile: ProfileResponse,
    val profileCompleteness: Int,
    val updatedAt: String
)
