# ADR-0005: PostgreSQL as Command Store

## Status

Accepted

## Context

The command side of our CQRS architecture requires a database that:

- Provides strong ACID transactional guarantees for event persistence
- Supports optimistic concurrency control for aggregate versioning
- Offers mature Change Data Capture capabilities for event streaming
- Has excellent operational tooling and community support
- Scales adequately for our write workloads

Candidates considered:
- **PostgreSQL**: Mature, feature-rich relational database with excellent CDC support
- **MySQL**: Popular relational database but less sophisticated CDC and JSON support
- **EventStoreDB**: Purpose-built for event sourcing but adds operational complexity
- **CockroachDB**: Distributed SQL with global consistency but higher complexity

## Decision

We will use **PostgreSQL** as the command store for all services implementing event sourcing.

Schema design for event storage:
```sql
CREATE TABLE events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    metadata JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (aggregate_id, version)
);

CREATE INDEX idx_events_aggregate ON events (aggregate_id, version);
CREATE INDEX idx_events_type ON events (event_type);
CREATE INDEX idx_events_created_at ON events (created_at);
```

Concurrency control:
- Optimistic locking via unique constraint on `(aggregate_id, version)`
- First writer wins; concurrent updates receive version conflict error
- Client retries with latest state on conflict

Configuration highlights:
- Logical replication enabled for Debezium CDC
- Connection pooling via PgBouncer or built-in pooling
- Regular VACUUM and index maintenance
- Point-in-time recovery enabled for disaster recovery

## Consequences

### Positive

- **Battle-Tested**: PostgreSQL is mature with decades of production hardening
- **ACID Compliance**: Strong transactional guarantees for event consistency
- **CDC Support**: Native logical replication works seamlessly with Debezium
- **JSONB Flexibility**: Store event payloads as structured JSON with indexing
- **Ecosystem**: Rich tooling, monitoring, and operational expertise available
- **Cost**: Open source with no licensing costs

### Negative

- **Vertical Scaling Limits**: Single-node write capacity has upper bounds
- **Operational Overhead**: Requires maintenance (vacuuming, index tuning)
- **Not Purpose-Built**: Event sourcing patterns implemented on general-purpose DB

### Mitigations

- Use read replicas to offload read queries from primary
- Implement event archival to manage table size over time
- Consider partitioning by time for very high-volume event streams
- Monitor transaction log (WAL) size and replication lag
