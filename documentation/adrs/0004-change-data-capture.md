# ADR-0004: Change Data Capture (CDC)

## Status

Accepted

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
```
┌─────────────┐     ┌────────────┐     ┌───────────────┐     ┌─────────┐
│   Service   │────▶│ PostgreSQL │────▶│   Debezium    │────▶│  Kafka  │
│             │     │   (WAL)    │     │   Connect     │     │         │
└─────────────┘     └────────────┘     └───────────────┘     └─────────┘
                           │
                    Transaction Log
                    (Write-Ahead Log)
```

How it works:
1. Service writes events to PostgreSQL within a transaction
2. PostgreSQL records changes in the Write-Ahead Log (WAL)
3. Debezium reads the WAL and captures row-level changes
4. Changes are published to Kafka topics (one per table)
5. Downstream consumers process events from Kafka

Debezium configuration:
- **Slot Name**: Dedicated replication slot per connector
- **Plugin**: `pgoutput` (built-in logical decoding)
- **Topic Routing**: `{server}.{schema}.{table}` naming convention
- **Transforms**: Extract event payloads, route to appropriate topics

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

- Use Schema Registry for schema evolution and compatibility checking
- Monitor replication slot lag with alerts for growing lag
- Implement event transformation in Kafka Streams or dedicated consumers
- Use Debezium's built-in transforms for simple field extraction
- Test connector configuration thoroughly before production deployment
