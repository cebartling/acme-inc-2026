package com.acme.customer.domain

/**
 * Enumeration of consent types for GDPR-compliant consent management.
 *
 * Each consent type represents a specific category of data processing
 * or communication that requires explicit customer consent.
 *
 * @property description Human-readable description of the consent type.
 * @property required Whether this consent is required for service delivery.
 * @property defaultGranted Whether this consent is granted by default at registration.
 */
enum class ConsentType(
    val description: String,
    val required: Boolean,
    val defaultGranted: Boolean
) {
    /**
     * Basic data processing for service delivery.
     * This consent is required and implicitly granted at registration.
     */
    DATA_PROCESSING(
        description = "Basic data processing for service delivery",
        required = true,
        defaultGranted = true
    ),

    /**
     * Marketing communications and promotions.
     * Optional consent, defaults to customer's registration choice.
     */
    MARKETING(
        description = "Marketing communications and promotions",
        required = false,
        defaultGranted = false
    ),

    /**
     * Usage analytics and behavior tracking.
     * Optional consent, not granted by default.
     */
    ANALYTICS(
        description = "Usage analytics and behavior tracking",
        required = false,
        defaultGranted = false
    ),

    /**
     * Data sharing with third-party partners.
     * Optional consent, not granted by default.
     */
    THIRD_PARTY(
        description = "Data sharing with partners",
        required = false,
        defaultGranted = false
    ),

    /**
     * Personalized recommendations based on behavior.
     * Optional consent, not granted by default.
     */
    PERSONALIZATION(
        description = "Personalized recommendations",
        required = false,
        defaultGranted = false
    );

    companion object {
        /**
         * Parses a string to a ConsentType, returning null if not found.
         *
         * @param value The string value to parse.
         * @return The matching ConsentType or null.
         */
        fun fromString(value: String): ConsentType? {
            return entries.find { it.name.equals(value, ignoreCase = true) }
        }
    }
}
