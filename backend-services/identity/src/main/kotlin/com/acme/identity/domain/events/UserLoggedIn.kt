package com.acme.identity.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Payload data for the [UserLoggedIn] domain event.
 *
 * Contains comprehensive information about the successful login for
 * auditing, security monitoring, and analytics.
 *
 * @property userId The unique identifier of the user.
 * @property sessionId The session ID created for this login.
 * @property ipAddress The IP address of the client.
 * @property userAgent The User-Agent header from the request.
 * @property deviceFingerprint The device fingerprint if available.
 * @property mfaUsed Whether MFA was used during authentication.
 * @property mfaMethod The MFA method used (TOTP, SMS, or null if not used).
 * @property loginSource The source of the login (WEB, MOBILE_APP, etc.).
 */
data class UserLoggedInPayload(
    val userId: UUID,
    val sessionId: String,
    val ipAddress: String,
    val userAgent: String,
    val deviceFingerprint: String?,
    val mfaUsed: Boolean,
    val mfaMethod: String?,
    val loginSource: String
)

/**
 * Domain event published when a user successfully completes the login process.
 *
 * This event is published after all authentication steps are complete,
 * including MFA if required. It indicates that the user is now fully
 * authenticated and a session has been created.
 *
 * This event is persisted to the event store and published to Kafka
 * to trigger downstream processes such as:
 * - Audit logging and compliance
 * - Security monitoring and fraud detection
 * - Analytics and user behavior tracking
 * - Welcome notifications or post-login workflows
 *
 * @property payload The event payload containing login details.
 * @see DomainEvent
 * @see UserLoggedInPayload
 */
class UserLoggedIn(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: UserLoggedInPayload
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
        const val EVENT_TYPE = "UserLoggedIn"

        /** The schema version for this event. */
        const val EVENT_VERSION = "1.0"

        /** The aggregate type this event belongs to. */
        const val AGGREGATE_TYPE = "User"

        /** The Kafka topic where this event is published. */
        const val TOPIC = "identity.authentication.events"

        /**
         * Factory method to create a new [UserLoggedIn] event.
         *
         * Automatically generates the event ID and timestamp.
         *
         * @param userId The user's unique ID.
         * @param sessionId The session ID for this login.
         * @param ipAddress The client's IP address.
         * @param userAgent The client's User-Agent header.
         * @param deviceFingerprint Optional device fingerprint.
         * @param mfaUsed Whether MFA was used.
         * @param mfaMethod The MFA method used (TOTP, SMS, or null).
         * @param loginSource The login source (WEB, MOBILE_APP, etc.).
         * @param correlationId Optional correlation ID for distributed tracing.
         * @return A new [UserLoggedIn] event instance.
         */
        fun create(
            userId: UUID,
            sessionId: String,
            ipAddress: String,
            userAgent: String,
            deviceFingerprint: String? = null,
            mfaUsed: Boolean,
            mfaMethod: String? = null,
            loginSource: String = "WEB",
            correlationId: UUID = UUID.randomUUID()
        ): UserLoggedIn {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return UserLoggedIn(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = userId,
                correlationId = correlationId,
                payload = UserLoggedInPayload(
                    userId = userId,
                    sessionId = sessionId,
                    ipAddress = ipAddress,
                    userAgent = userAgent,
                    deviceFingerprint = deviceFingerprint,
                    mfaUsed = mfaUsed,
                    mfaMethod = mfaMethod,
                    loginSource = loginSource
                )
            )
        }
    }
}
