# ADR-0004: Change Data Capture (CDC)

## Status

Accepted, amended in [PIN-534](https://linear.app/pintail-consulting/issue/PIN-534/wire-debezium-cdc-into-cqrs-for-all-services)

**Amended 2026-11-14 ([PIN-534](https://linear.app/pintail-consulting/issue/PIN-534/wire-debezium-cdc-into-cqrs-for-all-services), PIN-545):** How change data capture works in the services is now settled, and the Decision below reflects it. This is a decision, not yet implemented: the services still run as described under "Current implementation" at the end of this block.

- **Outbox tables.** A service writes its events to an outbox table in the same transaction as its state change. Debezium's outbox event router publishes them to the existing topics, with unchanged keys and JSON payloads, so existing consumers keep working. Debezium does not capture the services' state tables.
- **Product captures tables directly.** Product has no write path: its catalog comes from Flyway seeds. Debezium captures the catalog tables, and the connector's first snapshot loads the seeded catalog into MongoDB. This is the one exception to the outbox rule.
- **Product's analytics events keep publishing directly.** `ProductViewed`, `SearchExecuted` and `FiltersApplied` are not state changes, so there is nothing to capture. They are the only events that stay on direct `KafkaTemplate` sends.
- **Connectors.** One connector per service database, registered automatically by the local stack ([ADR-0008](0008-debezium-cdc-connector.md)).
- **No polling relay.** The polling `OutboxRelay` is replaced by Debezium ([ADR-0022](0022-transactional-outbox-pattern.md)), in the order service too (PIN-552).
- **Order has no read model.** The order service owns `acme_orders` and has no MongoDB read model (PIN-552), so only its events travel through CDC.
- **Identity's Redis-backed events.** Session, device and token-reuse events describe state that lives in Redis. Writing them to a PostgreSQL outbox gives them one path to Kafka, but not atomicity with the Redis change; the outbox write opens its own transaction there (PIN-548, [ADR-0067](0067-identity-outbox-and-users-read-model.md)).

**Current implementation (2026-10-21, PIN-386; shopping-cart moved to an outbox in [ADR-0060](0060-cart-events-through-outbox.md) since; connector registration automated in PIN-546):** Debezium Connect runs in `docker-compose.yml`, but no service publishes its events through CDC. No connector config exists yet: PIN-546 removed the unused `acme-postgres-connector` (`acme_orders`) and added the `debezium-connector-init` container, which registers every `docker/debezium/connectors/*.json` with Connect when the application stack starts. Customer and order instead write events to a transactional outbox table that a scheduled `OutboxRelay` polls about once a second and sends to Kafka, which is the polling pattern described below. Shopping-cart also writes to a polled outbox ([ADR-0060](0060-cart-events-through-outbox.md)). Product publishes straight to Kafka ([ADR-0042](0042-best-effort-event-publishing.md)); identity and notification write to an outbox that Debezium publishes ([ADR-0067](0067-identity-outbox-and-users-read-model.md), PIN-548; [ADR-0068](0068-notification-outbox-and-deliveries-read-model.md), PIN-549). Customer's MongoDB read model is projected in-process by `CustomerReadModelProjector`, not fed by CDC ([ADR-0041](0041-per-service-persistence-model.md)). Schema Registry (see Mitigations) is not used.

## Context

In an event-sourced microservices architecture, services need to reliably publish events whenever state changes. Common approaches have significant drawbacks:

**Dual-Write Problem**:
Writing to the database and publishing to Kafka in application code creates consistency issues:
- If the database write succeeds but Kafka publish fails, data is persisted but events are lost
- If Kafka publish succeeds but database write fails, events are published for uncommitted data
- Two-phase commit is complex and reduces availability

**Polling Pattern**:
Periodically polling tables for changes:
- Introduces latency between change and detection
- Creates load on the database
- Requires tracking which records have been processed
- May miss rapid successive updates

We need a reliable mechanism to capture database changes and stream them to Kafka without the dual-write problem.

## Decision

We will use **Change Data Capture (CDC)** via **Debezium** to stream database changes from PostgreSQL to Kafka.

Architecture:

```mermaid
flowchart LR
    Service[Service] --> PG[(PostgreSQL<br/>WAL)]
    PG --> DC[Debezium<br/>Connect]
    DC --> Kafka[(Kafka)]

    PG -.-> WAL[Transaction Log<br/>Write-Ahead Log]
```

How it works (amended 2026-11-14, PIN-534):
1. The service writes its state change and an outbox row in one transaction ([ADR-0022](0022-transactional-outbox-pattern.md))
2. PostgreSQL records the outbox insert in the Write-Ahead Log (WAL)
3. Debezium reads the WAL and captures the outbox row
4. Debezium's outbox event router publishes the row's payload to the row's topic, keyed by its message key
5. Downstream consumers and the service's own projector process the events from Kafka

Product is the exception: Debezium captures its catalog tables directly and publishes row-level changes to one topic per table.

Debezium configuration:
- **Slot Name**: Dedicated replication slot per connector
- **Plugin**: `pgoutput` (built-in logical decoding)
- **Topic Routing**: Outbox rows go to the topic in their `topic` column. Product's table capture uses `{server}.{schema}.{table}`.
- **Transforms**: The outbox event router (`io.debezium.transforms.outbox.EventRouter`) for outbox rows
- **Serialization**: JSON ([ADR-0008](0008-debezium-cdc-connector.md))

## Consequences

### Positive

- **Guaranteed Delivery**: If it's committed to the database, it will reach Kafka
- **Low Latency**: Near real-time streaming (milliseconds after commit)
- **No Application Changes**: Capture works at database level, invisible to application code
- **Exactly-Once Processing**: Kafka offsets + idempotent consumers ensure no duplicates
- **Full Event History**: Can capture initial snapshot plus ongoing changes

### Negative

- **Infrastructure Dependency**: Adds Debezium Connect cluster to maintain
- **Schema Coupling**: Changes to database schema affect downstream consumers
- **Replication Slot Management**: Must monitor slot lag to prevent WAL bloat
- **Limited Transformation**: Complex event transformations require additional processing

### Mitigations

- Keep event payloads backward compatible. There is no Schema Registry ([ADR-0008](0008-debezium-cdc-connector.md)), so consumers and producers agree on JSON shapes by contract.
- Monitor replication slot lag with alerts for growing lag
- Implement event transformation in Kafka Streams or dedicated consumers
- Use Debezium's built-in transforms for simple field extraction
- Test connector configuration thoroughly before production deployment
