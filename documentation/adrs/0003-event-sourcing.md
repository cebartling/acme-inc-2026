# ADR-0003: Event Sourcing

## Status

Accepted

## Context

Traditional state-based persistence stores only the current state of entities. This approach:

- Loses the history of how an entity reached its current state
- Makes temporal queries impossible (e.g., "what was the order status at 3pm yesterday?")
- Cannot support features requiring historical analysis or audit trails
- Struggles with distributed consistency in microservices architectures

The ACME e-commerce platform has strong requirements for:
- **Audit Compliance**: Track all changes for regulatory and business requirements
- **Debugging**: Understand exactly what happened when issues occur
- **Analytics**: Analyze historical patterns and trends
- **Feature Flexibility**: Rebuild read models to support new query requirements

## Decision

We will implement **Event Sourcing** as the persistence strategy for the command side of our services.

Core principles:
- **Events as Source of Truth**: All state changes are captured as immutable, ordered events
- **Aggregate Reconstruction**: Current state is derived by replaying events from the event store
- **Append-Only**: Events are never modified or deleted, only appended
- **Event Naming**: Events describe facts in past tense (e.g., `OrderPlaced`, `PaymentReceived`)

Event structure:
```json
{
  "eventId": "uuid",
  "aggregateId": "uuid",
  "aggregateType": "Order",
  "eventType": "OrderPlaced",
  "version": 1,
  "timestamp": "2026-01-06T12:00:00Z",
  "payload": {
    "orderId": "uuid",
    "customerId": "uuid",
    "items": [...],
    "totalAmount": 99.99
  },
  "metadata": {
    "correlationId": "uuid",
    "causationId": "uuid",
    "userId": "uuid"
  }
}
```

Storage strategy:
- Events stored in PostgreSQL with optimistic concurrency control
- Published to Kafka for distribution to other services and read model projectors
- Snapshots taken periodically to optimize aggregate loading for aggregates with many events

## Consequences

### Positive

- **Complete Audit Trail**: Every state change is recorded with timestamp and actor
- **Temporal Queries**: Query state at any point in time by replaying events up to that moment
- **Debugging**: Reproduce exact sequence of events that led to any state
- **Read Model Flexibility**: Build new read models by replaying historical events
- **Natural Event Distribution**: Events are already in the format needed for event-driven architecture

### Negative

- **Learning Curve**: Event sourcing requires a different mental model than CRUD
- **Event Schema Evolution**: Events are immutable, requiring careful schema versioning strategy
- **Query Complexity**: Cannot directly query current state without maintaining projections
- **Storage Growth**: Event streams grow indefinitely, requiring retention policies
- **Eventual Consistency**: Derived state may lag behind event writes

### Mitigations

- Use event upcasting to handle schema evolution transparently
- Implement snapshot strategy to optimize aggregate loading
- Define retention policies: archive old events to cold storage, maintain snapshots
- Provide clear guidelines and training for event design
- Accept eventual consistency for read models; use aggregate state for command validation
