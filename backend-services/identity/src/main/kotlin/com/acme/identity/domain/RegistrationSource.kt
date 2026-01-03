package com.acme.identity.domain

/**
 * Identifies the origin channel through which a user registered.
 *
 * This is used for analytics, tracking, and potentially applying
 * source-specific business rules during registration.
 */
enum class RegistrationSource {
    /**
     * Registration originated from the web application.
     */
    WEB,

    /**
     * Registration originated from a mobile application (iOS or Android).
     */
    MOBILE,

    /**
     * Registration originated from a direct API integration,
     * typically used by partner systems or B2B integrations.
     */
    API
}
