package com.acme.customer.domain.events

import com.acme.customer.domain.ConsentSource
import com.acme.customer.domain.ConsentType
import java.time.Instant
import java.util.UUID

/**
 * Payload data for the [ConsentGranted] domain event.
 *
 * Contains all information about a consent that was granted.
 *
 * @property customerId The unique identifier of the customer.
 * @property consentId The unique identifier of the consent record.
 * @property consentType The type of consent that was granted.
 * @property grantedAt Timestamp when the consent was granted.
 * @property source Where the consent was granted from.
 * @property expiresAt When the consent expires (null if never).
 * @property version The version number of this consent type.
 */
data class ConsentGrantedPayload(
    val customerId: UUID,
    val consentId: UUID,
    val consentType: String,
    val grantedAt: Instant,
    val source: String,
    val expiresAt: Instant?,
    val version: Int
)

/**
 * Domain event published when a customer grants consent for data processing.
 *
 * This event is persisted to the event store and published to Kafka
 * to notify downstream services (e.g., Marketing, Analytics, Compliance)
 * that a customer has granted consent.
 *
 * @property payload The event payload containing consent details.
 * @see DomainEvent
 * @see ConsentGrantedPayload
 */
class ConsentGranted(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    causationId: UUID? = null,
    val payload: ConsentGrantedPayload
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
        const val EVENT_TYPE = "ConsentGranted"

        /** The schema version for this event. */
        const val EVENT_VERSION = "1.0"

        /** The aggregate type this event belongs to. */
        const val AGGREGATE_TYPE = "Customer"

        /** The Kafka topic where this event is published. */
        const val TOPIC = "customer.events"

        /**
         * Factory method to create a new [ConsentGranted] event.
         *
         * Automatically generates the event ID and timestamp.
         *
         * @param customerId The unique identifier for the customer.
         * @param consentId The unique identifier for the consent record.
         * @param consentType The type of consent granted.
         * @param grantedAt When the consent was granted.
         * @param source Where the consent was granted from.
         * @param expiresAt When the consent expires (null if never).
         * @param version The version number of this consent type.
         * @param correlationId Correlation ID for distributed tracing.
         * @param causationId Optional causation ID linking to triggering event.
         * @return A new [ConsentGranted] event instance.
         */
        fun create(
            customerId: UUID,
            consentId: UUID,
            consentType: ConsentType,
            grantedAt: Instant,
            source: ConsentSource,
            expiresAt: Instant?,
            version: Int,
            correlationId: UUID,
            causationId: UUID? = null
        ): ConsentGranted {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return ConsentGranted(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = customerId,
                correlationId = correlationId,
                causationId = causationId,
                payload = ConsentGrantedPayload(
                    customerId = customerId,
                    consentId = consentId,
                    consentType = consentType.name,
                    grantedAt = grantedAt,
                    source = source.name,
                    expiresAt = expiresAt,
                    version = version
                )
            )
        }
    }
}
