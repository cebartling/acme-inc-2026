package com.acme.customer.domain.events

import com.acme.customer.domain.ConsentSource
import com.acme.customer.domain.ConsentType
import java.time.Instant
import java.util.UUID

/**
 * Payload data for the [ConsentRevoked] domain event.
 *
 * Contains all information about a consent that was revoked.
 *
 * @property customerId The unique identifier of the customer.
 * @property consentId The unique identifier of the consent record.
 * @property consentType The type of consent that was revoked.
 * @property revokedAt Timestamp when the consent was revoked.
 * @property source Where the consent was revoked from.
 * @property version The version number of this consent type.
 */
data class ConsentRevokedPayload(
    val customerId: UUID,
    val consentId: UUID,
    val consentType: String,
    val revokedAt: Instant,
    val source: String,
    val version: Int
)

/**
 * Domain event published when a customer revokes consent for data processing.
 *
 * This event is persisted to the event store and published to Kafka
 * to notify downstream services (e.g., Marketing, Analytics, Compliance)
 * that a customer has revoked consent. Downstream systems should stop
 * processing data for this consent type within 24 hours.
 *
 * @property payload The event payload containing consent details.
 * @see DomainEvent
 * @see ConsentRevokedPayload
 */
class ConsentRevoked(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    causationId: UUID? = null,
    val payload: ConsentRevokedPayload
) : DomainEvent(
    eventId = eventId,
    eventType = EVENT_TYPE,
    eventVersion = EVENT_VERSION,
    timestamp = timestamp,
    aggregateId = aggregateId,
    aggregateType = AGGREGATE_TYPE,
    correlationId = correlationId,
    causationId = causationId
) {
    companion object {
        /** The event type identifier. */
        const val EVENT_TYPE = "ConsentRevoked"

        /** The schema version for this event. */
        const val EVENT_VERSION = "1.0"

        /** The aggregate type this event belongs to. */
        const val AGGREGATE_TYPE = "Customer"

        /** The Kafka topic where this event is published. */
        const val TOPIC = "customer.events"

        /**
         * Factory method to create a new [ConsentRevoked] event.
         *
         * Automatically generates the event ID and timestamp.
         *
         * @param customerId The unique identifier for the customer.
         * @param consentId The unique identifier for the consent record.
         * @param consentType The type of consent revoked.
         * @param revokedAt When the consent was revoked.
         * @param source Where the consent was revoked from.
         * @param version The version number of this consent type.
         * @param correlationId Correlation ID for distributed tracing.
         * @param causationId Optional causation ID linking to triggering event.
         * @return A new [ConsentRevoked] event instance.
         */
        fun create(
            customerId: UUID,
            consentId: UUID,
            consentType: ConsentType,
            revokedAt: Instant,
            source: ConsentSource,
            version: Int,
            correlationId: UUID,
            causationId: UUID? = null
        ): ConsentRevoked {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return ConsentRevoked(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = customerId,
                correlationId = correlationId,
                causationId = causationId,
                payload = ConsentRevokedPayload(
                    customerId = customerId,
                    consentId = consentId,
                    consentType = consentType.name,
                    revokedAt = revokedAt,
                    source = source.name,
                    version = version
                )
            )
        }
    }
}
