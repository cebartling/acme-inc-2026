# ADR-0013: Loki for Log Aggregation

## Status

Accepted

## Context

Distributed microservices generate logs across many containers and hosts. We need centralized logging that:

- Aggregates logs from all services into a single queryable store
- Correlates logs with distributed traces via trace IDs
- Provides efficient search without expensive full-text indexing
- Integrates with our Grafana-based observability stack
- Scales cost-effectively with log volume

Candidates considered:
- **Loki**: Grafana's log aggregation system, label-based indexing
- **Elasticsearch**: Full-text search, powerful but resource-intensive
- **Splunk**: Enterprise features but high licensing costs
- **CloudWatch Logs**: AWS-specific, limited query capabilities

## Decision

We will use **Grafana Loki** for centralized log aggregation.

Architecture:
```
┌──────────────────────────────────────────────────────────────┐
│                          Loki                                 │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────┐ │
│  │ Distributor │  │  Ingester  │  │   Querier  │  │ Store  │ │
│  └────────────┘  └────────────┘  └────────────┘  └────────┘ │
└──────────────────────────────────────────────────────────────┘
         ▲                                      │
         │ push                                 │ query
    ┌────┴─────────────────┐                    ▼
    │     Log Shipper      │              ┌──────────┐
    │  (Promtail/Fluent)   │              │ Grafana  │
    └──────────────────────┘              └──────────┘
         ▲         ▲
    ┌────┘         └────┐
    │                   │
┌───┴───┐           ┌───┴───┐
│Service│           │Service│
│ logs  │           │ logs  │
└───────┘           └───────┘
```

Log format (structured JSON):
```json
{
  "timestamp": "2026-01-06T12:00:00.000Z",
  "level": "INFO",
  "logger": "com.acme.identity.AuthController",
  "message": "User login successful",
  "traceId": "abc123def456",
  "spanId": "789ghi",
  "service": "identity-service",
  "userId": "user-uuid",
  "method": "POST",
  "path": "/auth/login"
}
```

Label strategy:
- **Indexed labels** (low cardinality): `service`, `level`, `environment`
- **Structured metadata** (high cardinality): `traceId`, `userId`, `path`

LogQL query examples:
```logql
# Errors in identity service
{service="identity-service"} |= "error" | json

# Logs for specific trace
{service=~".+"} | json | traceId="abc123def456"

# Rate of errors by service
sum by (service) (rate({level="ERROR"}[5m]))
```

## Consequences

### Positive

- **Cost Efficient**: Only indexes labels, not log content
- **Grafana Integration**: Native integration, uses same query interface
- **Trace Correlation**: Link logs to Tempo traces via trace ID
- **Prometheus-Like**: Familiar labeling and querying model
- **Scalable**: Handles high log volumes with efficient compression

### Negative

- **No Full-Text Index**: Grep-style search on log content, not inverted index
- **Label Cardinality**: High cardinality labels degrade performance
- **Query Complexity**: LogQL less expressive than Elasticsearch queries

### Mitigations

- Use structured logging (JSON) for efficient field extraction
- Keep label cardinality low; use structured metadata for high-cardinality fields
- Create pre-defined LogQL queries for common investigations
- Use recording rules for frequently-used aggregations
