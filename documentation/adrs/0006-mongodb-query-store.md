# ADR-0006: MongoDB as Query Store

## Status

Accepted

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
