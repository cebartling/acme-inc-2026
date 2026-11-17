package com.acme.notification.infrastructure.projection

/**
 * Layout of a notification delivery document in MongoDB (PIN-549, ADR-0068), shared by the
 * projector (writes) and the queries class (reads).
 *
 * ```
 * { _id: notificationId, recipientId, recipientEmail, createdAt,   // the lookups a reader uses; createdAt is a BSON date
 *   snapshot: { notificationId, notificationType, recipientId, recipientEmail, status, attemptCount,
 *               providerMessageId, sentAt, deliveredAt, bouncedAt, bounceReason, createdAt },
 *   appliedEventIds: [ ...the last MAX_APPLIED_EVENT_IDS source events ] }
 * ```
 */
object NotificationReadModelDocument {
    const val COLLECTION = "notification_delivery_read_models"

    const val ID = "_id"
    const val RECIPIENT_ID = "recipientId"
    const val RECIPIENT_EMAIL = "recipientEmail"
    const val CREATED_AT = "createdAt"
    const val SNAPSHOT = "snapshot"
    const val APPLIED_EVENT_IDS = "appliedEventIds"

    /** Enough to find an event's snapshot even if later ones have landed. */
    const val MAX_APPLIED_EVENT_IDS = 50
}
