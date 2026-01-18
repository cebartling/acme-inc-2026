package com.acme.identity.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Reason codes for authentication failures.
 */
enum class AuthenticationFailureReason {
    /** The email address was not found in the system. */
    USER_NOT_FOUND,

    /** The password did not match the stored hash. */
    INVALID_PASSWORD,

    /** The account is not in ACTIVE status. */
    ACCOUNT_INACTIVE,

    /** The account is locked due to too many failed attempts. */
    ACCOUNT_LOCKED,

    /** The request was rate limited. */
    RATE_LIMITED
}

/**
 * Payload data for the [AuthenticationFailed] domain event.
 *
 * Contains information about the failed authentication attempt for
 * security monitoring and fraud detection.
 *
 * @property email The email address used in the signin attempt.
 * @property reason The reason for the authentication failure.
 * @property ipAddress The IP address of the client making the request.
 * @property userAgent The User-Agent header from the request.
 * @property failedAttemptCount The total number of failed attempts for this account (if known).
 * @property deviceFingerprint Optional device fingerprint from the request.
 */
data class AuthenticationFailedPayload(
    val email: String,
    val reason: AuthenticationFailureReason,
    val ipAddress: String,
    val userAgent: String,
    val failedAttemptCount: Int,
    val deviceFingerprint: String? = null
)

/**
 * Domain event published when an authentication attempt fails.
 *
 * This event is persisted to the event store and published to Kafka
 * to trigger downstream processes such as:
 * - Security monitoring and alerting
 * - Fraud detection systems
 * - Account lockout processing
 * - Audit logging
 *
 * @property payload The event payload containing failure details.
 * @see DomainEvent
 * @see AuthenticationFailedPayload
 */
class AuthenticationFailed(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: AuthenticationFailedPayload
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
        const val EVENT_TYPE = "AuthenticationFailed"

        /** The schema version for this event. */
        const val EVENT_VERSION = "1.0"

        /** The aggregate type this event belongs to. */
        const val AGGREGATE_TYPE = "User"

        /** The Kafka topic where this event is published. */
        const val TOPIC = "identity.authentication.events"

        /**
         * Factory method to create a new [AuthenticationFailed] event.
         *
         * Automatically generates the event ID and timestamp.
         *
         * @param userId The user ID if known (use UUID(0,0) for unknown users).
         * @param email The email used in the signin attempt.
         * @param reason The reason for the failure.
         * @param ipAddress The client's IP address.
         * @param userAgent The client's User-Agent header.
         * @param failedAttemptCount The number of failed attempts for this account.
         * @param deviceFingerprint Optional device fingerprint.
         * @param correlationId Optional correlation ID for distributed tracing.
         * @return A new [AuthenticationFailed] event instance.
         */
        fun create(
            userId: UUID,
            email: String,
            reason: AuthenticationFailureReason,
            ipAddress: String,
            userAgent: String,
            failedAttemptCount: Int,
            deviceFingerprint: String? = null,
            correlationId: UUID = UUID.randomUUID()
        ): AuthenticationFailed {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return AuthenticationFailed(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = userId,
                correlationId = correlationId,
                payload = AuthenticationFailedPayload(
                    email = email,
                    reason = reason,
                    ipAddress = ipAddress,
                    userAgent = userAgent,
                    failedAttemptCount = failedAttemptCount,
                    deviceFingerprint = deviceFingerprint
                )
            )
        }

        /**
         * Factory method for creating an event when user is not found.
         *
         * Uses a nil UUID for the aggregate ID since the user doesn't exist.
         */
        fun forUnknownUser(
            email: String,
            reason: AuthenticationFailureReason,
            ipAddress: String,
            userAgent: String,
            deviceFingerprint: String? = null,
            correlationId: UUID = UUID.randomUUID()
        ): AuthenticationFailed {
            return create(
                userId = UUID(0, 0), // Nil UUID for unknown users
                email = email,
                reason = reason,
                ipAddress = ipAddress,
                userAgent = userAgent,
                failedAttemptCount = 0,
                deviceFingerprint = deviceFingerprint,
                correlationId = correlationId
            )
        }
    }
}
