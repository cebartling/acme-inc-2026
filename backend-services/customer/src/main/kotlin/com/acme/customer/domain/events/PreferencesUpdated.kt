package com.acme.customer.domain.events

import com.acme.customer.domain.PreferenceChange
import java.time.Instant
import java.util.UUID

/**
 * Payload data for the [PreferencesUpdated] domain event.
 *
 * Contains the customer ID and the map of changed preferences
 * with their old and new values.
 *
 * @property customerId The unique identifier of the customer.
 * @property changedPreferences Map of preference names to their old/new values.
 */
data class PreferencesUpdatedPayload(
    val customerId: UUID,
    val changedPreferences: Map<String, PreferenceChangePayload>
)

/**
 * Serializable representation of a preference change.
 *
 * @property old The previous value (may be null).
 * @property new The new value.
 */
data class PreferenceChangePayload(
    val old: String?,
    val new: String
) {
    companion object {
        /**
         * Creates a payload from a PreferenceChange.
         */
        fun fromDomain(change: PreferenceChange): PreferenceChangePayload {
            return PreferenceChangePayload(
                old = change.oldValue,
                new = change.newValue
            )
        }
    }
}

/**
 * Domain event published when a customer's preferences are updated.
 *
 * This event is persisted to the event store and published to Kafka
 * to notify downstream services (e.g., Notification Service) that
 * a customer's preferences have changed.
 *
 * @property payload The event payload containing preference changes.
 * @see DomainEvent
 * @see PreferencesUpdatedPayload
 */
class PreferencesUpdated(
    eventId: UUID,
    timestamp: Instant,
    aggregateId: UUID,
    correlationId: UUID,
    causationId: UUID? = null,
    val payload: PreferencesUpdatedPayload
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
        const val EVENT_TYPE = "PreferencesUpdated"

        /** The schema version for this event. */
        const val EVENT_VERSION = "1.0"

        /** The aggregate type this event belongs to. */
        const val AGGREGATE_TYPE = "Customer"

        /** The Kafka topic where this event is published. */
        const val TOPIC = "customer.events"

        /**
         * Factory method to create a new [PreferencesUpdated] event.
         *
         * Automatically generates the event ID and timestamp.
         *
         * @param customerId The unique identifier for the customer.
         * @param changedPreferences Map of changed preference names to their old/new values.
         * @param correlationId Correlation ID for distributed tracing.
         * @param causationId Optional causation ID linking to triggering event.
         * @return A new [PreferencesUpdated] event instance.
         */
        fun create(
            customerId: UUID,
            changedPreferences: Map<String, PreferenceChange>,
            correlationId: UUID,
            causationId: UUID? = null
        ): PreferencesUpdated {
            val eventId = UUID.randomUUID()
            val timestamp = Instant.now()

            return PreferencesUpdated(
                eventId = eventId,
                timestamp = timestamp,
                aggregateId = customerId,
                correlationId = correlationId,
                causationId = causationId,
                payload = PreferencesUpdatedPayload(
                    customerId = customerId,
                    changedPreferences = changedPreferences.mapValues { (_, change) ->
                        PreferenceChangePayload.fromDomain(change)
                    }
                )
            )
        }
    }
}
