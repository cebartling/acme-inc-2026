package com.acme.identity.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Payload data for the [TokenReuseDetected] domain event.
 *
 * Recorded when a refresh-token JWT is presented whose `tokenFamily` claim
 * does not match the session's current tokenFamily — a strong signal of
 * refresh-token replay (the original token was already rotated, so any
 * subsequent presentation of it indicates either a stolen cookie or a
 * legitimate but stale client trying to refresh out-of-band).
 *
 * Per OWASP guidance on refresh-token rotation, detecting reuse triggers
 * invalidation of every session for the affected user (not just the
 * targeted one) — the [sessionsInvalidatedCount] captures how many were
 * killed so downstream consumers (security monitoring, customer notifications)
 * can size the blast radius without re-querying.
 *
 * @property sessionId The session whose refresh token was replayed.
 * @property userId The user who owns the session.
 * @property sessionTokenFamily The tokenFamily currently held by the session
 *           (i.e. the legitimate, most-recently-rotated family).
 * @property presentedTokenFamily The tokenFamily on the inbound JWT (stale).
 * @property sessionsInvalidatedCount Number of sessions invalidated as part
 *           of the reuse-detection sweep for this user.
 * @property detectedAt When the reuse was detected.
 */
data class TokenReuseDetectedPayload(
    val sessionId: String,
    val userId: UUID,
    val sessionTokenFamily: String,
    val presentedTokenFamily: String,
    val sessionsInvalidatedCount: Int,
    val detectedAt: Instant
)

/**
 * Domain event published when refresh-token reuse is detected.
 *
 * Persisted to the event store and published to Kafka on the
 * `identity.session.events` topic (same topic as [SessionInvalidated] so a
 * single consumer can observe the security narrative end-to-end).
 *
 * @property payload The reuse-detection details.
 * @see DomainEvent
 * @see TokenReuseDetectedPayload
 * @see SessionInvalidated
 */
class TokenReuseDetected(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: String,
    correlationId: UUID,
    val payload: TokenReuseDetectedPayload
) : DomainEvent(
    eventId = eventId,
    eventType = EVENT_TYPE,
    eventVersion = EVENT_VERSION,
    timestamp = timestamp,
    aggregateId = UUID.fromString(aggregateId.removePrefix("sess_")),
    aggregateType = AGGREGATE_TYPE,
    correlationId = correlationId
) {
    companion object {
        /** The event type identifier. */
        const val EVENT_TYPE = "TokenReuseDetected"

        /** The schema version for this event. */
        const val EVENT_VERSION = "1.0"

        /** The aggregate type this event belongs to. */
        const val AGGREGATE_TYPE = "Session"

        /**
         * Reuses the existing `identity.session.events` topic so downstream
         * consumers can observe the SessionInvalidated and TokenReuseDetected
         * events in a single subscription.
         */
        const val TOPIC = "identity.session.events"

        /**
         * Factory method to create a new [TokenReuseDetected] event.
         *
         * Automatically generates the event ID and stamps the timestamp.
         *
         * @param sessionId The session ID that received the replayed token.
         * @param userId The user owning the session.
         * @param sessionTokenFamily The current (legitimate) tokenFamily.
         * @param presentedTokenFamily The stale tokenFamily on the JWT.
         * @param sessionsInvalidatedCount Number of sessions killed in the sweep.
         * @param correlationId Optional correlation ID for distributed tracing.
         */
        fun create(
            sessionId: String,
            userId: UUID,
            sessionTokenFamily: String,
            presentedTokenFamily: String,
            sessionsInvalidatedCount: Int,
            correlationId: UUID = UUID.randomUUID()
        ): TokenReuseDetected {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return TokenReuseDetected(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = sessionId,
                correlationId = correlationId,
                payload = TokenReuseDetectedPayload(
                    sessionId = sessionId,
                    userId = userId,
                    sessionTokenFamily = sessionTokenFamily,
                    presentedTokenFamily = presentedTokenFamily,
                    sessionsInvalidatedCount = sessionsInvalidatedCount,
                    detectedAt = timestamp
                )
            )
        }
    }
}
