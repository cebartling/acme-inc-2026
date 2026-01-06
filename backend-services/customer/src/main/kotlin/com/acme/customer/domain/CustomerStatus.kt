package com.acme.customer.domain

/**
 * Represents the lifecycle status of a customer account.
 *
 * The status follows a defined lifecycle:
 * 1. PENDING_VERIFICATION - Initial state after registration
 * 2. ACTIVE - Email verified and account fully active
 * 3. SUSPENDED - Account temporarily disabled
 * 4. DELETED - Account permanently deleted (soft delete)
 */
enum class CustomerStatus {
    /**
     * Customer has registered but email is not yet verified.
     * This is the initial state for all new customers.
     */
    PENDING_VERIFICATION,

    /**
     * Customer has verified their email and account is fully active.
     * Customer can perform all operations.
     */
    ACTIVE,

    /**
     * Customer account is temporarily suspended.
     * May be due to policy violations or at customer request.
     */
    SUSPENDED,

    /**
     * Customer account has been permanently deleted (soft delete).
     * Data is retained for legal/audit purposes but account is inaccessible.
     */
    DELETED
}
