package com.acme.customer.domain

/**
 * Represents how often notifications should be batched and sent to customers.
 *
 * @property IMMEDIATE Send notifications as they occur.
 * @property DAILY_DIGEST Batch notifications into a daily summary (9 AM local time).
 * @property WEEKLY_DIGEST Batch notifications into a weekly summary (Monday 9 AM local time).
 */
enum class NotificationFrequency {
    /** Send notifications immediately as they occur. */
    IMMEDIATE,

    /** Batch notifications into a daily digest (9 AM local time). */
    DAILY_DIGEST,

    /** Batch notifications into a weekly digest (Monday 9 AM local time). */
    WEEKLY_DIGEST;

    companion object {
        /**
         * Safely parses a string to NotificationFrequency.
         *
         * @param value The string value to parse.
         * @return The parsed NotificationFrequency or null if invalid.
         */
        fun fromString(value: String?): NotificationFrequency? {
            return value?.let {
                try {
                    valueOf(it.uppercase())
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
        }
    }
}
