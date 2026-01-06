# ADR-0002: Command Query Responsibility Segregation (CQRS)

## Status

Accepted

## Context

The ACME e-commerce platform has fundamentally different requirements for read and write operations:

**Write Operations (Commands)**:
- Require strong consistency and transactional guarantees
- Must validate business rules before persisting state
- Generate events for downstream consumers
- Have relatively lower throughput compared to reads

**Read Operations (Queries)**:
- Dominate traffic (typically 90%+ of operations in e-commerce)
- Benefit from denormalized data optimized for specific query patterns
- Can tolerate slightly stale data in exchange for performance
- Require flexible query capabilities for search, filtering, and reporting

Traditional CRUD patterns force a single data model to serve both purposes, leading to:
- Complex schemas that compromise between normalized writes and denormalized reads
- Contention between write transactions and long-running read queries
- Inability to scale reads and writes independently

## Decision

We will implement **Command Query Responsibility Segregation (CQRS)** to separate the read and write models for each service.

Architecture:
- **Command Side**: PostgreSQL as the system of record for write operations
- **Query Side**: MongoDB as the optimized read store for queries
- **Synchronization**: Events published via Kafka keep read models eventually consistent with writes

Data flow:
1. Commands are validated and persisted to PostgreSQL (source of truth)
2. Domain events are published to Kafka
3. Query-side projectors consume events and update MongoDB read models
4. Queries are served directly from MongoDB

Service structure:
```
┌─────────────────────────────────────────────────────┐
│                     Service                          │
│  ┌─────────────────┐    ┌─────────────────────────┐ │
│  │  Command Side   │    │      Query Side          │ │
│  │  - Validation   │    │  - Projectors            │ │
│  │  - Business     │    │  - Query Handlers        │ │
│  │    Logic        │    │  - Search Optimization   │ │
│  │  - PostgreSQL   │    │  - MongoDB               │ │
│  └────────┬────────┘    └──────────┬──────────────┘ │
│           │                        │                 │
│           └──────────┬─────────────┘                 │
│                      │                               │
└──────────────────────┼───────────────────────────────┘
                       │
                   ┌───▼───┐
                   │ Kafka │
                   └───────┘
```

## Consequences

### Positive

- **Optimized Models**: Read and write models are independently optimized for their specific access patterns
- **Independent Scaling**: Scale read and write infrastructure separately based on load
- **Query Flexibility**: MongoDB's document model enables complex queries without JOIN overhead
- **Performance**: Denormalized read models eliminate expensive runtime calculations
- **Separation of Concerns**: Clear boundaries between command validation and query optimization

### Negative

- **Eventual Consistency**: Read models may lag slightly behind writes (typically milliseconds to seconds)
- **Complexity**: Two data stores per service increases operational and development overhead
- **Data Duplication**: Same data stored in different formats across PostgreSQL and MongoDB
- **Synchronization Failures**: Must handle scenarios where read model updates fail

### Mitigations

- Accept eventual consistency where business allows; use read-your-writes consistency for critical UX
- Implement idempotent projectors to handle replay and retry scenarios
- Monitor read model lag as a key operational metric
- Use dead letter queues for failed projection updates with alerting
