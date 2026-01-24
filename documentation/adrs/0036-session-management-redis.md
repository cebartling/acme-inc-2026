# ADR-0036: Session Management with Redis

## Status

Accepted

## Context

The Identity Service needs to track active user sessions for several purposes:

1. **Token Revocation**: Ability to invalidate tokens before expiry
2. **Concurrent Session Limits**: Enforce maximum sessions per user (5 devices)
3. **Device Tracking**: Monitor and audit user devices and locations
4. **Security Monitoring**: Detect suspicious login patterns
5. **Session Metadata**: Store session-related information (IP, User-Agent, device)

While JWTs are stateless by design, we need server-side session storage for:
- Immediate session invalidation (logout, security events)
- Enforcing business rules (concurrent session limits)
- Audit and compliance requirements

We need a session storage solution that is:
- **Fast**: Low-latency reads/writes (< 10ms p95)
- **Scalable**: Handles millions of concurrent sessions
- **TTL Support**: Automatic session expiration
- **Highly Available**: No single point of failure
- **Queryable**: Find sessions by user ID

## Decision

We will use **Redis** for session storage with Spring Data Redis.

### 1. Session Structure

Sessions are stored as Redis hashes with automatic TTL:

```kotlin
@RedisHash("sessions")
data class Session(
    @Id
    val id: String,              // sess_{UUID}

    @Indexed
    val userId: UUID,            // Enables findByUserId queries

    val deviceId: String,        // Device identifier
    val ipAddress: String,       // Client IP
    val userAgent: String,       // Client User-Agent
    val tokenFamily: String,     // For refresh token rotation
    val createdAt: Instant,      // Session creation time
    val expiresAt: Instant,      // Session expiration time

    @TimeToLive(unit = TimeUnit.SECONDS)
    val ttl: Long                // Redis TTL (7 days = 604800s)
)
```

### 2. Storage Pattern

- **Key Pattern**: `sessions:{sessionId}`
- **TTL**: 7 days (matches refresh token expiry)
- **Indexing**: Secondary index on `userId` for user session queries
- **Eviction**: Automatic via Redis TTL (no manual cleanup required)

### 3. Concurrent Session Limit

Enforce maximum 5 sessions per user:

1. Query existing sessions: `findByUserId(userId)`
2. If count >= 5, evict oldest session (by `createdAt`)
3. Create new session
4. Publish `SessionInvalidated` event for evicted session

### 4. Repository Pattern

Use Spring Data Redis repository abstraction:

```kotlin
@Repository
interface SessionRepository : CrudRepository<Session, String> {
    fun findByUserId(userId: UUID): List<Session>
    fun countByUserId(userId: UUID): Long
}
```

### 5. Redis Configuration

**Connection**:
- Host: Configurable via `REDIS_HOST` (default: localhost)
- Port: Configurable via `REDIS_PORT` (default: 6379)
- Client: Lettuce (async, non-blocking)
- Pool: 8 connections (max-active, max-idle)

**Serialization**:
- Keys: String serialization (human-readable)
- Values: JSON serialization (Kotlin data class support)

### 6. High Availability

**Current**: Single Redis instance (development)

**Production Recommendations**:
- Redis Sentinel for automatic failover
- Redis Cluster for horizontal scaling
- AOF persistence for durability
- Regular backups for disaster recovery

## Consequences

### Positive

1. **Performance**: Redis provides sub-millisecond latency for session operations
2. **Automatic Expiration**: TTL-based cleanup eliminates manual session management
3. **Scalability**: Redis scales horizontally via clustering
4. **Simplicity**: Spring Data Redis abstracts low-level operations
5. **Developer Experience**: Kotlin data classes map cleanly to Redis hashes
6. **Indexed Queries**: Find all sessions for a user efficiently
7. **Atomic Operations**: Redis ensures consistency for concurrent session checks

### Negative

1. **Additional Infrastructure**: Requires Redis deployment and monitoring
2. **Single Point of Failure**: Without HA setup, Redis downtime = no sessions
3. **Memory Constraints**: Sessions are stored in memory (costs scale with users)
4. **Network Dependency**: Session operations require network round-trip to Redis
5. **Consistency Model**: Eventual consistency in clustered setups

### Risks

1. **Redis Outage**: Sessions become inaccessible, users must re-authenticate
   - **Mitigation**: Redis Sentinel/Cluster, fallback to stateless mode
2. **Memory Exhaustion**: Too many sessions can exhaust Redis memory
   - **Mitigation**: Proper TTL configuration, monitoring, eviction policies
3. **Session Hijacking**: Compromised session ID grants access
   - **Mitigation**: Secure cookies, IP/device validation, session binding

## Implementation Details

- **Entity**: `Session` domain model with `@RedisHash`
- **Repository**: `SessionRepository` for CRUD operations
- **Service**: `SessionService` for business logic
- **Configuration**: `RedisConfig` with serializers
- **Properties**: `SessionConfig` for session limits and TTL

## Performance Considerations

1. **Read Operations**: O(1) for session lookups by ID
2. **User Queries**: O(N) where N = sessions per user (max 5)
3. **Session Creation**: Single HSET operation (< 1ms)
4. **Session Deletion**: Single DEL operation (< 1ms)
5. **TTL Management**: Automatic via Redis, no application overhead

## Monitoring

**Key Metrics to Track**:
- Session creation rate
- Session eviction rate (concurrent limit)
- Redis memory usage
- Redis connection pool saturation
- Session query latency (p50, p95, p99)
- Active sessions per user (histogram)

## Alternatives Considered

### 1. PostgreSQL Session Table

**Pros**: Already have PostgreSQL, ACID guarantees
**Cons**: Slower (10-50ms vs 1ms), no native TTL, requires cleanup job
**Verdict**: ❌ Too slow for session operations

### 2. In-Memory (Caffeine Cache)

**Pros**: Fastest (< 1ms), no network overhead
**Cons**: Not distributed, lost on restart, no HA
**Verdict**: ❌ Not suitable for production

### 3. DynamoDB

**Pros**: Fully managed, scalable, TTL support
**Cons**: Higher latency (5-10ms), cost, AWS lock-in
**Verdict**: ❌ Overkill for current scale

### 4. Memcached

**Pros**: Simple, fast, proven
**Cons**: No persistence, no indexing, no complex data structures
**Verdict**: ❌ Redis is strictly better for our use case

## Future Enhancements

1. **Redis Sentinel**: Automatic failover for high availability
2. **Session Analytics**: Track login patterns, device usage
3. **Geographic Sessions**: Store sessions in region-local Redis
4. **Session Renewal**: Extend sessions on activity
5. **Device Management**: UI for users to view/revoke sessions

## References

- Spring Data Redis Documentation
- Redis Best Practices
- Session Management Security (OWASP)
- Lettuce Redis Client Documentation

## Related Decisions

- ADR-0035: JWT Token Implementation
- ADR-0007: Apache Kafka for Event Streaming
- ADR-0018: Idempotent Event Processing

## Date

2026-01-22
