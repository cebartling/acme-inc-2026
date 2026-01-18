package com.acme.identity.domain

/**
 * Represents the lifecycle status of a user account.
 *
 * Users progress through these statuses as they complete verification
 * and interact with the system.
 */
enum class UserStatus {
    /**
     * Initial status after registration. User must verify their email
     * before gaining full access to the system.
     */
    PENDING_VERIFICATION,

    /**
     * User has completed email verification and has full access
     * to the system.
     */
    ACTIVE,

    /**
     * User account has been temporarily suspended, typically due to
     * policy violations or security concerns.
     */
    SUSPENDED,

    /**
     * User account has been temporarily locked due to too many
     * failed signin attempts. Will automatically unlock after
     * the lockout period expires.
     */
    LOCKED,

    /**
     * User account has been deactivated by the user or an admin.
     * Can be reactivated through customer support.
     */
    DEACTIVATED,

    /**
     * User account has been soft-deleted. The record is retained
     * for audit purposes but the user can no longer access the system.
     */
    DELETED
}
