package com.acme.notification.domain

/**
 * Status of a notification delivery attempt.
 *
 * Tracks the lifecycle of a notification from creation to final delivery.
 */
enum class NotificationStatus {
    /**
     * Notification has been queued for sending.
     */
    PENDING,

    /**
     * Notification was accepted by the email provider.
     */
    SENT,

    /**
     * Notification was delivered to the recipient.
     */
    DELIVERED,

    /**
     * Notification bounced (hard or soft bounce).
     */
    BOUNCED,

    /**
     * Notification sending failed after all retries.
     */
    FAILED
}
