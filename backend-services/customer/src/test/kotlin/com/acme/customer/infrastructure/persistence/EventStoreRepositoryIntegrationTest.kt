package com.acme.customer.infrastructure.persistence

import com.acme.customer.config.JacksonConfig
import com.acme.customer.domain.CustomerStatus
import com.acme.customer.domain.CustomerType
import com.acme.customer.domain.events.CustomerRegistered
import com.acme.customer.domain.events.CustomerRegisteredPayload
import com.acme.customer.domain.events.DomainEvent
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises [EventStoreRepository]'s hand-written SQL against the real `event_store`
 * schema from V4 (PIN-277).
 *
 * `event_store` has no `@Entity`, so `SchemaValidationTest` cannot reach it — Hibernate
 * validates mappings, and there is no mapping here. Every statement in this repository is
 * a string, so a column renamed in a migration breaks it at runtime with nothing failing
 * first. Seven use cases append through it ([com.acme.customer.application.CreateCustomerUseCase]
 * among them), which makes it the event-sourcing write path for the whole service.
 *
 * [JacksonConfig] is imported rather than an ObjectMapper built here, so the payload is
 * serialized exactly as production serializes it — ISO-8601 instants rather than numeric
 * timestamps. A locally built mapper would test a different serializer than the one that
 * runs.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(EventStoreRepository::class, JacksonConfig::class)
@Testcontainers
class EventStoreRepositoryIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("acme_customers_test")
            .withUsername("test")
            .withPassword("test")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }

    @Autowired
    private lateinit var repository: EventStoreRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    /** A minimal event, for the cases [CustomerRegistered] cannot express (null causation). */
    private class TestEvent(
        eventId: UUID = UUID.randomUUID(),
        timestamp: Instant = Instant.parse("2026-01-01T00:00:00Z"),
        aggregateId: UUID = UUID.randomUUID(),
        correlationId: UUID = UUID.randomUUID(),
        causationId: UUID? = null,
        val note: String = "test"
    ) : DomainEvent(
        eventId = eventId,
        eventType = "TestEvent",
        eventVersion = "1.0",
        timestamp = timestamp,
        aggregateId = aggregateId,
        aggregateType = "Customer",
        correlationId = correlationId,
        causationId = causationId
    )

    private fun customerRegistered(
        aggregateId: UUID = UUID.randomUUID(),
        correlationId: UUID = UUID.randomUUID(),
        timestamp: Instant = Instant.parse("2026-01-01T12:00:00Z")
    ) = CustomerRegistered(
        eventId = UUID.randomUUID(),
        timestamp = timestamp,
        aggregateId = aggregateId,
        correlationId = correlationId,
        causationId = UUID.randomUUID(),
        payload = CustomerRegisteredPayload(
            customerId = aggregateId,
            userId = UUID.randomUUID(),
            customerNumber = "ACME-202601-000001",
            email = "customer@example.com",
            firstName = "Ada",
            lastName = "Lovelace",
            status = CustomerStatus.ACTIVE,
            type = CustomerType.INDIVIDUAL,
            registeredAt = timestamp
        )
    )

    @Test
    fun `append should write every column the INSERT names`() {
        // Given
        val event = customerRegistered()

        // When — a column renamed in a migration fails here, not in production
        repository.append(event)

        // Then
        val row = jdbcTemplate.queryForMap(
            "SELECT * FROM event_store WHERE event_id = ?",
            event.eventId
        )
        assertEquals(event.eventId, row["event_id"])
        assertEquals("CustomerRegistered", row["event_type"])
        assertEquals("1.0", row["event_version"])
        assertEquals(event.aggregateId, row["aggregate_id"])
        assertEquals("Customer", row["aggregate_type"])
        assertEquals(event.correlationId, row["correlation_id"])
        assertEquals(event.causationId, row["causation_id"])
        assertNotNull(row["payload"])
    }

    @Test
    fun `append should rely on the created_at default rather than supplying it`() {
        // The INSERT omits created_at, so the column's DEFAULT NOW() is load-bearing.
        // Dropping that default in a later migration would break every append.
        val event = customerRegistered()

        repository.append(event)

        val createdAt = jdbcTemplate.queryForObject(
            "SELECT created_at FROM event_store WHERE event_id = ?",
            java.sql.Timestamp::class.java,
            event.eventId
        )
        assertNotNull(createdAt, "created_at must be populated by the column default")
    }

    @Test
    fun `append should store the payload as real jsonb`() {
        // Given
        val event = customerRegistered()

        // When
        repository.append(event)

        // Then — these operators only work if the ?::jsonb cast produced real jsonb,
        // and only find these keys if production's ObjectMapper shape is preserved.
        val email = jdbcTemplate.queryForObject(
            "SELECT payload->'payload'->>'email' FROM event_store WHERE event_id = ?",
            String::class.java,
            event.eventId
        )
        assertEquals("customer@example.com", email)

        val registeredAt = jdbcTemplate.queryForObject(
            "SELECT payload->'payload'->>'registeredAt' FROM event_store WHERE event_id = ?",
            String::class.java,
            event.eventId
        )
        assertEquals(
            "2026-01-01T12:00:00Z",
            registeredAt,
            "instants must serialize as ISO-8601, per JacksonConfig"
        )
    }

    @Test
    fun `append should accept a null causation id`() {
        // causation_id is the one nullable column; DomainEvent defaults it to null.
        val event = TestEvent(causationId = null)

        repository.append(event)

        val causationId = jdbcTemplate.queryForObject(
            "SELECT causation_id FROM event_store WHERE event_id = ?",
            UUID::class.java,
            event.eventId
        )
        assertNull(causationId)
    }

    @Test
    fun `findByAggregateId should return that aggregate's events oldest first`() {
        // Given — appended newest first, so ordering cannot pass by insertion accident
        val aggregateId = UUID.randomUUID()
        val second = TestEvent(
            aggregateId = aggregateId,
            timestamp = Instant.parse("2026-01-01T02:00:00Z"),
            note = "second"
        )
        val first = TestEvent(
            aggregateId = aggregateId,
            timestamp = Instant.parse("2026-01-01T01:00:00Z"),
            note = "first"
        )
        repository.append(second)
        repository.append(first)
        repository.append(TestEvent(note = "other aggregate"))

        // When
        val events = repository.findByAggregateId(aggregateId)

        // Then
        assertEquals(2, events.size, "events from other aggregates must not be returned")
        assertEquals(listOf(first.eventId, second.eventId), events.map { it["event_id"] })
    }

    @Test
    fun `findByAggregateId should select every column it names`() {
        // Given
        val event = customerRegistered()
        repository.append(event)

        // When
        val row = repository.findByAggregateId(event.aggregateId).single()

        // Then — the SELECT's column list, verified against what comes back
        assertEquals(
            setOf(
                "event_id", "event_type", "event_version", "timestamp",
                "aggregate_id", "aggregate_type", "correlation_id", "causation_id", "payload"
            ),
            row.keys
        )
        assertEquals(event.timestamp, (row["timestamp"] as java.sql.Timestamp).toInstant())
    }

    @Test
    fun `findByCorrelationId should return every event sharing the correlation`() {
        // Given — one correlation spanning two aggregates, as a traced flow would
        val correlationId = UUID.randomUUID()
        val a = TestEvent(correlationId = correlationId, timestamp = Instant.parse("2026-01-01T01:00:00Z"))
        val b = TestEvent(correlationId = correlationId, timestamp = Instant.parse("2026-01-01T02:00:00Z"))
        repository.append(b)
        repository.append(a)
        repository.append(TestEvent())

        // When
        val events = repository.findByCorrelationId(correlationId)

        // Then
        assertEquals(listOf(a.eventId, b.eventId), events.map { it["event_id"] })
    }

    @Test
    fun `findByAggregateId should return empty for an unknown aggregate`() {
        assertTrue(repository.findByAggregateId(UUID.randomUUID()).isEmpty())
    }
}
