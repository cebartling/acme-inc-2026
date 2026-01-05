package com.acme.customer.domain

/**
 * Represents the type of customer account.
 *
 * Different customer types may have different features,
 * pricing, and terms available to them.
 */
enum class CustomerType {
    /**
     * Individual consumer account.
     * Default type for most registrations.
     */
    INDIVIDUAL,

    /**
     * Business/corporate account.
     * May have access to business-specific features like invoicing,
     * multiple users, and volume pricing.
     */
    BUSINESS
}
