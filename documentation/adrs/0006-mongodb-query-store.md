# ADR-0006: MongoDB as Query Store

## Status

Accepted, amended in [PIN-534](https://linear.app/pintail-consulting/issue/PIN-534/wire-debezium-cdc-into-cqrs-for-all-services)

Narrowed in scope by [ADR-0041](0041-per-service-persistence-model.md): this describes the target for a service that needs it, not what every service does today. The amendment below supersedes that narrowing for five of the six services.

**Amended 2026-11-14 ([PIN-534](https://linear.app/pintail-consulting/issue/PIN-534/wire-debezium-cdc-into-cqrs-for-all-services), PIN-545):** This is a decision, not yet implemented: today only customer writes a read model, and nothing reads one. Five services (identity, customer, notification, product, shopping-cart) get MongoDB read models, kept current by projectors that consume the events Debezium publishes ([ADR-0004](0004-change-data-capture.md)). This restores the "all services" scope of the Decision below for those five. The order service is deferred: none of PIN-534's sub-issues covers it, so it gets no read model until a separate issue does.

- **Serve reads from MongoDB:** customer, product, shopping-cart and notification serve their read endpoints from their read models. Customer's profile reads move off PostgreSQL and the Redis profile cache.
- **Identity gets a users read model.** Its other reads stay where they are, each for its own reason:

  | Read | Stays in | Why |
  |------|----------|-----|
  | JWKS | Memory | The signing keys are loaded at startup, not stored as data that changes |
  | Email verification | PostgreSQL | It changes state, so it is a command, not a query |
  | Password-reset token check | PostgreSQL | It must be current: a stale read could accept a used or expired token |
  | Device list | Redis | Redis is the system of record for device trusts, so a projection would only copy it |

- **Layout:** every service uses the shared `acme_query` database (customer's today) and owns its own collections. A service reads and writes only its own collections. This is a convention: the database and its credentials are shared, so nothing enforces it.
- **Read-your-writes.** Projections lag writes slightly, so a screen that reads right after a change can show stale data (the cart, stock availability during cart stock checks, a profile just edited). Each service handles this, for example by returning the new state from the command.

## Context

The query side of our CQRS architecture requires a database optimized for:

- Fast reads with flexible query patterns (search, filtering, aggregation)
- Denormalized document storage matching API response shapes
- Horizontal scalability for read-heavy workloads
- Schema flexibility to evolve read models without migrations
- Support for complex queries without expensive JOINs

Candidates considered:
- **MongoDB**: Document database with flexible schemas and excellent query capabilities
- **Elasticsearch**: Powerful full-text search but higher operational complexity
- **PostgreSQL**: Could share command store but loses optimization separation
- **Redis**: Fast but limited query capabilities beyond key-value lookups

## Decision

We will use **MongoDB** as the query store for read models across all services.

Collection design principles:
- **One collection per read model**: Optimize for specific query patterns
- **Embed related data**: Denormalize to avoid cross-document lookups
- **API-shaped documents**: Structure documents to match API response format
- **Computed fields**: Pre-calculate aggregations at write time

Example read model for customer orders:
```json
{
  "_id": "order-uuid",
  "customerId": "customer-uuid",
  "customerName": "John Doe",
  "status": "shipped",
  "items": [
    {
      "productId": "product-uuid",
      "productName": "Widget",
      "quantity": 2,
      "unitPrice": 29.99,
      "lineTotal": 59.98
    }
  ],
  "totals": {
    "subtotal": 59.98,
    "tax": 4.80,
    "shipping": 5.99,
    "total": 70.77
  },
  "timeline": [
    {"status": "placed", "timestamp": "2026-01-05T10:00:00Z"},
    {"status": "paid", "timestamp": "2026-01-05T10:01:00Z"},
    {"status": "shipped", "timestamp": "2026-01-06T14:30:00Z"}
  ],
  "updatedAt": "2026-01-06T14:30:00Z"
}
```

Indexing strategy:
- Create indexes for all query predicates
- Use compound indexes for common filter combinations
- Leverage text indexes for search functionality

## Consequences

### Positive

- **Query Performance**: Denormalized documents serve queries without JOINs
- **Schema Flexibility**: Evolve document structure without migrations
- **Horizontal Scaling**: Sharding distributes read load across nodes
- **Developer Experience**: JSON documents map directly to API responses
- **Rich Queries**: Aggregation pipeline supports complex analytics

### Negative

- **Data Duplication**: Same data stored differently than command store
- **Consistency Lag**: Read models are eventually consistent with writes
- **Storage Costs**: Denormalization increases storage requirements
- **No Transactions Across Documents**: Updates to related documents aren't atomic

### Mitigations

- Monitor projection lag as key metric; alert on growing delays
- Use idempotent projectors to handle event replay safely
- Implement bulk write operations for efficiency
- Accept eventual consistency where business permits
