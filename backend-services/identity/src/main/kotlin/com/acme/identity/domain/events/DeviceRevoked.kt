package com.acme.identity.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Reason for device trust revocation.
 */
enum class DeviceRevocationReason {
    /** User manually revoked the device from settings. */
    USER_REVOKED,

    /** User revoked all trusted devices. */
    USER_REVOKED_ALL,

    /** Device trust automatically expired (30 days). */
    EXPIRED,

    /** Password change triggered revocation of all devices. */
    PASSWORD_CHANGED,

    /** Device limit exceeded, oldest device evicted. */
    LIMIT_EXCEEDED,

    /** Admin or system revoked the device. */
    ADMIN_REVOKED
}

/**
 * Payload data for the [DeviceRevoked] domain event.
 *
 * Contains information about the device trust revocation for
 * auditing and security monitoring.
 *
 * @property userId The unique identifier of the user.
 * @property deviceTrustId The unique identifier of the revoked device trust.
 * @property reason The reason for revocation.
 * @property revokedAt When the device trust was revoked.
 */
data class DeviceRevokedPayload(
    val userId: UUID,
    val deviceTrustId: String,
    val reason: DeviceRevocationReason,
    val revokedAt: Instant
)

/**
 * Domain event published when a device trust is revoked.
 *
 * This event is persisted to the event store and published to Kafka
 * to trigger downstream processes such as:
 * - Audit logging
 * - Security monitoring (unusual revocation patterns)
 * - Analytics and reporting
 * - Notification service (optional email: "Device trust removed")
 *
 * @property payload The event payload containing revocation details.
 * @see DomainEvent
 * @see DeviceRevokedPayload
 */
class DeviceRevoked(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: DeviceRevokedPayload
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
        const val EVENT_TYPE = "DeviceRevoked"

        /** The schema version for this event. */
        const val EVENT_VERSION = "1.0"

        /** The aggregate type this event belongs to. */
        const val AGGREGATE_TYPE = "User"

        /** The Kafka topic where this event is published. */
        const val TOPIC = "identity.device.events"

        /**
         * Factory method to create a new [DeviceRevoked] event.
         *
         * Automatically generates the event ID and timestamp.
         *
         * @param userId The user's ID.
         * @param deviceTrustId The device trust ID.
         * @param reason The reason for revocation.
         * @param correlationId Optional correlation ID for distributed tracing.
         * @return A new [DeviceRevoked] event instance.
         */
        fun create(
            userId: UUID,
            deviceTrustId: String,
            reason: DeviceRevocationReason,
            correlationId: UUID = UUID.randomUUID()
        ): DeviceRevoked {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return DeviceRevoked(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = userId,
                correlationId = correlationId,
                payload = DeviceRevokedPayload(
                    userId = userId,
                    deviceTrustId = deviceTrustId,
                    reason = reason,
                    revokedAt = timestamp
                )
            )
        }
    }
}
