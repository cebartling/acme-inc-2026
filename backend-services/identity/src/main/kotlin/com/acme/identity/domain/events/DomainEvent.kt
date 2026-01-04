package com.acme.identity.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Abstract base class for all domain events in the identity service.
 *
 * Domain events represent significant occurrences within the domain that
 * other parts of the system may need to react to. Events are immutable
 * and contain all information necessary to describe what happened.
 *
 * Events are persisted to the event store and published to Kafka for
 * consumption by other services.
 *
 * @property eventId Unique identifier for this specific event instance.
 * @property eventType The type name of the event (e.g., "UserRegistered").
 * @property eventVersion Schema version of the event for evolution support.
 * @property timestamp When the event occurred.
 * @property aggregateId The ID of the aggregate (entity) this event relates to.
 * @property aggregateType The type of aggregate (e.g., "User").
 * @property correlationId ID for tracing related events across services.
 */
abstract class DomainEvent(
    val eventId: UUID,
    val eventType: String,
    val eventVersion: String,
    val timestamp: Instant,
    val aggregateId: UUID,
    val aggregateType: String,
    val correlationId: UUID
)
