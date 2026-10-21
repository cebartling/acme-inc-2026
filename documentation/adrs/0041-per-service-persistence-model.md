# ADR-0041: Per-Service Persistence Model

## Status

Accepted

Narrows the scope of [ADR-0002](0002-cqrs-pattern.md), [ADR-0003](0003-event-sourcing.md)
and [ADR-0006](0006-mongodb-query-store.md), which describe CQRS, event sourcing and a
MongoDB read model for every service.

## Context

ADR-0002, ADR-0003 and ADR-0006 were written before most services existed. Together they
describe one model for every service: events as the source of truth, Kafka-fed projectors,
and a MongoDB read model on the query side.

The services built since then don't work that way, and none of their stories asked for it:

- **Customer** saves its state with JPA in PostgreSQL and also appends to an `event_store`
  table (`V4__create_event_store_table.sql`). Its MongoDB read model is written in-process:
  `CreateCustomerUseCase`, `ActivateCustomerUseCase` and `UpdateProfileUseCase` call
  `CustomerReadModelProjector.projectCustomer`, an `@Async` method. It does not go through
  Kafka or CDC.
- **Identity** saves JPA state and appends to its own `event_store`
  (`V3__create_event_store_table.sql`). It has no read model.
- **Product, shopping cart and order** use plain JPA with Flyway and `ddl-auto: validate`.
  They have no event store and no read model. The order epic's event store and CQRS read
  models were deferred explicitly when the service was created (PIN-364, PIN-366).

No service rebuilds an aggregate from its events. In customer and identity,
`EventStoreRepository.findByAggregateId` exists, but no production code calls it. The JPA
rows are the source of truth, and the event store is a history kept beside them.

## Decision

Each service picks its persistence model by what it needs, with this as the default:

1. **The source of truth is the service's JPA state in its own PostgreSQL database**
   (ADR-0005), with Flyway migrations and `ddl-auto: validate`.
2. **An `event_store` table, where a service has one, is an append-only audit history,**
   written in the same transaction as the state change. It is not event sourcing: state is
   not rebuilt from it.
3. **A separate read model is added only when a query need justifies it.** Today that is
   customer's MongoDB `customers` collection, written in-process by
   `CustomerReadModelProjector`.
4. **Events for other services go to Kafka.** They go through the outbox (ADR-0022) or a
   best-effort publish, depending on the service. They are a way to integrate services, not
   a way to store state.

Full event sourcing or CQRS for a service, such as the order lifecycle in Epic 005, needs
its own ADR when a story calls for it.

## Consequences

### Positive

- Each service is plain Spring Data JPA, which is quick to build, test against
  Testcontainers Postgres, and reason about.
- Queries can run against the source of truth, so there's no read-model lag to explain to
  users.
- The audit history in customer and identity still records every change.

### Negative

- ADR-0002, ADR-0003 and ADR-0006 overstate what is built. They stay Accepted, now limited
  in scope by this ADR.
- No service can rebuild its state or a new read model by replaying events. A new
  projection needs a backfill from the JPA tables.
- Customer's in-process projection can leave MongoDB behind PostgreSQL when a projection
  fails. There is no replay from Kafka or CDC to repair it.

### Mitigations

- A new read model or event-sourced aggregate gets its own ADR, saying what it reads from
  and how it is backfilled.
- `ARCHITECTURE.md` describes each service's actual persistence, and links here.
