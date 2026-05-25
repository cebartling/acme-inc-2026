package com.acme.product.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Abstract base class for all domain events in the product service.
 *
 * @property eventId Unique identifier for this event instance.
 * @property eventType The type name of the event.
 * @property eventVersion Schema version for evolution support.
 * @property timestamp When the event occurred.
 * @property aggregateId The ID of the aggregate this event relates to.
 * @property aggregateType The type of aggregate.
 * @property correlationId ID for tracing related events across services.
 * @property causationId ID of the event that caused this event.
 */
abstract class DomainEvent(
    val eventId: UUID,
    val eventType: String,
    val eventVersion: String,
    val timestamp: Instant,
    val aggregateId: UUID,
    val aggregateType: String,
    val correlationId: UUID,
    val causationId: UUID? = null
)
