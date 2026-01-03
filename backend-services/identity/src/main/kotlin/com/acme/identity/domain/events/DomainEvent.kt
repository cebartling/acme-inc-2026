package com.acme.identity.domain.events

import java.time.Instant
import java.util.UUID

abstract class DomainEvent(
    val eventId: UUID,
    val eventType: String,
    val eventVersion: String,
    val timestamp: Instant,
    val aggregateId: UUID,
    val aggregateType: String,
    val correlationId: UUID
)
