package com.acme.identity.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Reason codes for account lockout.
 */
enum class AccountLockReason {
    /** Account locked due to too many consecutive failed signin attempts. */
    EXCESSIVE_FAILED_ATTEMPTS,

    /** Account locked by administrator action. */
    ADMIN_ACTION,

    /** Account locked due to suspicious activity detected. */
    SUSPICIOUS_ACTIVITY
}

/**
 * Payload data for the [AccountLocked] domain event.
 *
 * Contains information about the account lockout for security monitoring,
 * notifications, and analytics.
 *
 * @property userId The unique identifier of the locked user account.
 * @property email The email address of the locked account.
 * @property reason The reason for the account lockout.
 * @property failedAttemptCount The number of failed attempts that triggered the lockout.
 * @property lockedUntil The timestamp when the lockout will expire.
 * @property ipAddress The IP address of the request that triggered the lockout.
 * @property userAgent The User-Agent header from the request.
 */
data class AccountLockedPayload(
    val userId: UUID,
    val email: String,
    val reason: AccountLockReason,
    val failedAttemptCount: Int,
    val lockedUntil: Instant,
    val ipAddress: String,
    val userAgent: String
)

/**
 * Domain event published when an account is locked.
 *
 * This event is persisted to the event store and published to Kafka
 * to trigger downstream processes such as:
 * - Notification Service sending lockout email to customer
 * - Analytics tracking of lockout events
 * - Security monitoring and alerting
 * - Audit logging
 *
 * @property payload The event payload containing lockout details.
 * @see DomainEvent
 * @see AccountLockedPayload
 */
class AccountLocked(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: AccountLockedPayload
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
        const val EVENT_TYPE = "AccountLocked"

        /** The schema version for this event. */
        const val EVENT_VERSION = "1.0"

        /** The aggregate type this event belongs to. */
        const val AGGREGATE_TYPE = "User"

        /** The Kafka topic where this event is published. */
        const val TOPIC = "identity.account.events"

        /**
         * Factory method to create a new [AccountLocked] event.
         *
         * Automatically generates the event ID and timestamp.
         *
         * @param userId The unique identifier of the locked user.
         * @param email The email address of the locked account.
         * @param reason The reason for the lockout.
         * @param failedAttemptCount The number of failed attempts.
         * @param lockedUntil When the lockout expires.
         * @param ipAddress The client's IP address.
         * @param userAgent The client's User-Agent header.
         * @param correlationId Optional correlation ID for distributed tracing.
         * @return A new [AccountLocked] event instance.
         */
        fun create(
            userId: UUID,
            email: String,
            reason: AccountLockReason,
            failedAttemptCount: Int,
            lockedUntil: Instant,
            ipAddress: String,
            userAgent: String,
            correlationId: UUID = UUID.randomUUID()
        ): AccountLocked {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return AccountLocked(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = userId,
                correlationId = correlationId,
                payload = AccountLockedPayload(
                    userId = userId,
                    email = email,
                    reason = reason,
                    failedAttemptCount = failedAttemptCount,
                    lockedUntil = lockedUntil,
                    ipAddress = ipAddress,
                    userAgent = userAgent
                )
            )
        }
    }
}
