package com.acme.notification.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Base class for all domain events in the Notification Service.
 *
 * Domain events represent significant occurrences within the domain
 * and are used for event sourcing and inter-service communication.
 *
 * @property eventId Unique identifier for this event.
 * @property eventType The type of event (e.g., "NotificationSent").
 * @property eventVersion Schema version of this event type.
 * @property timestamp When the event occurred.
 * @property aggregateId The ID of the aggregate this event belongs to.
 * @property aggregateType The type of aggregate (e.g., "Notification").
 * @property correlationId ID for distributed tracing.
 */
abstract class DomainEvent(
    open val eventId: UUID,
    open val eventType: String,
    open val eventVersion: String,
    open val timestamp: Instant,
    open val aggregateId: UUID,
    open val aggregateType: String,
    open val correlationId: UUID
)
