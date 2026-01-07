package com.acme.notification.domain

/**
 * Types of notifications supported by the service.
 */
enum class NotificationType {
    /**
     * Email verification notification sent after user registration.
     */
    EMAIL_VERIFICATION,

    /**
     * Password reset notification.
     */
    PASSWORD_RESET,

    /**
     * Welcome email sent after email verification.
     */
    WELCOME,

    /**
     * Order confirmation notification.
     */
    ORDER_CONFIRMATION
}
