package com.acme.identity.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Reason codes for account unlock.
 */
enum class AccountUnlockReason {
    /** Lockout period has expired naturally. */
    LOCKOUT_EXPIRED,

    /** Account unlocked because user completed password reset. */
    PASSWORD_RESET,

    /** Account unlocked by administrator action. */
    ADMIN_ACTION
}

/**
 * Payload data for the [AccountUnlocked] domain event.
 *
 * Contains information about the account unlock for analytics and audit logging.
 *
 * @property userId The unique identifier of the unlocked user account.
 * @property email The email address of the unlocked account.
 * @property reason The reason for the account unlock.
 * @property unlockedAt The timestamp when the account was unlocked.
 * @property previousLockReason The reason the account was originally locked.
 */
data class AccountUnlockedPayload(
    val userId: UUID,
    val email: String,
    val reason: AccountUnlockReason,
    val unlockedAt: Instant,
    val previousLockReason: AccountLockReason? = null
)

/**
 * Domain event published when an account is unlocked.
 *
 * This event is persisted to the event store and published to Kafka
 * to trigger downstream processes such as:
 * - Analytics tracking of unlock events
 * - Audit logging
 *
 * @property payload The event payload containing unlock details.
 * @see DomainEvent
 * @see AccountUnlockedPayload
 */
class AccountUnlocked(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: AccountUnlockedPayload
) : DomainEvent(
    eventId = eventId,
    eventType = EVENT_TYPE,
    eventVersion = EVENT_VERSION,
    timestamp = timestamp,
    aggregateId = aggregateId,
    aggregateType = AGGREGATE_TYPE,
    correlationId = correlationId
) {
    companion object {
        /** The event type identifier. */
        const val EVENT_TYPE = "AccountUnlocked"

        /** The schema version for this event. */
        const val EVENT_VERSION = "1.0"

        /** The aggregate type this event belongs to. */
        const val AGGREGATE_TYPE = "User"

        /** The Kafka topic where this event is published. */
        const val TOPIC = "identity.account.events"

        /**
         * Factory method to create a new [AccountUnlocked] event.
         *
         * Automatically generates the event ID and timestamp.
         *
         * @param userId The unique identifier of the unlocked user.
         * @param email The email address of the unlocked account.
         * @param reason The reason for the unlock.
         * @param previousLockReason The reason the account was originally locked.
         * @param correlationId Optional correlation ID for distributed tracing.
         * @return A new [AccountUnlocked] event instance.
         */
        fun create(
            userId: UUID,
            email: String,
            reason: AccountUnlockReason,
            previousLockReason: AccountLockReason? = null,
            correlationId: UUID = UUID.randomUUID()
        ): AccountUnlocked {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return AccountUnlocked(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = userId,
                correlationId = correlationId,
                payload = AccountUnlockedPayload(
                    userId = userId,
                    email = email,
                    reason = reason,
                    unlockedAt = timestamp,
                    previousLockReason = previousLockReason
                )
            )
        }
    }
}
