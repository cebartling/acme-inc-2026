package com.acme.identity.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Payload data for the [DeviceRemembered] domain event.
 *
 * Contains information about the device trust creation for
 * auditing and security monitoring.
 *
 * @property userId The unique identifier of the user.
 * @property deviceTrustId The unique identifier of the device trust.
 * @property deviceFingerprint SHA-256 hash of the device fingerprint.
 * @property userAgent The User-Agent header from the request.
 * @property ipAddress The IP address where the device was remembered.
 * @property trustedUntil When the device trust expires.
 */
data class DeviceRememberedPayload(
    val userId: UUID,
    val deviceTrustId: String,
    val deviceFingerprint: String,
    val userAgent: String,
    val ipAddress: String,
    val trustedUntil: Instant
)

/**
 * Domain event published when a device is remembered for MFA bypass.
 *
 * This event is persisted to the event store and published to Kafka
 * to trigger downstream processes such as:
 * - Audit logging
 * - Security monitoring (unusual device patterns)
 * - Analytics and reporting
 * - Notification service (optional email: "New device trusted")
 *
 * @property payload The event payload containing device trust details.
 * @see DomainEvent
 * @see DeviceRememberedPayload
 */
class DeviceRemembered(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    val payload: DeviceRememberedPayload
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
        const val EVENT_TYPE = "DeviceRemembered"

        /** The schema version for this event. */
        const val EVENT_VERSION = "1.0"

        /** The aggregate type this event belongs to. */
        const val AGGREGATE_TYPE = "User"

        /** The Kafka topic where this event is published. */
        const val TOPIC = "identity.device.events"

        /**
         * Factory method to create a new [DeviceRemembered] event.
         *
         * Automatically generates the event ID and timestamp.
         *
         * @param userId The user's ID.
         * @param deviceTrustId The device trust ID.
         * @param deviceFingerprint SHA-256 hash of the fingerprint.
         * @param userAgent The client's User-Agent header.
         * @param ipAddress The client's IP address.
         * @param trustedUntil When the trust expires.
         * @param correlationId Optional correlation ID for distributed tracing.
         * @return A new [DeviceRemembered] event instance.
         */
        fun create(
            userId: UUID,
            deviceTrustId: String,
            deviceFingerprint: String,
            userAgent: String,
            ipAddress: String,
            trustedUntil: Instant,
            correlationId: UUID = UUID.randomUUID()
        ): DeviceRemembered {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return DeviceRemembered(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = userId,
                correlationId = correlationId,
                payload = DeviceRememberedPayload(
                    userId = userId,
                    deviceTrustId = deviceTrustId,
                    deviceFingerprint = deviceFingerprint,
                    userAgent = userAgent,
                    ipAddress = ipAddress,
                    trustedUntil = trustedUntil
                )
            )
        }
    }
}
