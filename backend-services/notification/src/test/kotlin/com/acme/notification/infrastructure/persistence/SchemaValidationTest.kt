package com.acme.notification.infrastructure.persistence

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Guards against divergence between this service's `@Entity` mappings and its Flyway
 * migrations (PIN-276).
 *
 * The service runs `hibernate.ddl-auto: validate`, so an entity whose table or column is
 * missing from the migrated schema is a hard startup failure. Every other test in this
 * module mocks its repositories, so that failure used to surface only when someone
 * started the Docker stack — never in CI.
 *
 * This test asserts nothing about behaviour on purpose. Flyway applying all migrations
 * and Hibernate validating every mapped entity against the result *is* the assertion;
 * reaching the test body already means both succeeded. That covers both entities whether
 * or not they are named below, so the `count()` calls are documentation — they list the
 * mapped tables and confirm each is queryable.
 *
 * Know what `validate` does *not* check, so this guard is not mistaken for a full schema
 * diff: it compares table and column existence and column type, not nullability, column
 * length, defaults, indexes or constraints. Verified on 2026-09-21 — widening
 * [ProcessedEvent.eventType] to `nullable = true, length = 500` against a
 * `VARCHAR(100) NOT NULL` column still passes; renaming the column fails.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class SchemaValidationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("acme_notifications_test")
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

    @Value("\${spring.jpa.hibernate.ddl-auto}")
    private lateinit var ddlAuto: String

    @Autowired
    private lateinit var deliveries: NotificationDeliveryRepository

    @Autowired
    private lateinit var processedEvents: ProcessedEventRepository

    @Test
    fun `every entity validates against the Flyway-migrated schema`() {
        // Without `validate`, Flyway would still create the tables and every count()
        // below would succeed — the test would pass while checking nothing. Assert the
        // service's real setting rather than pinning one, so this fails loudly if the
        // configuration that makes it a guard is ever changed.
        assertEquals(
            "validate",
            ddlAuto,
            "this test only guards against schema drift while ddl-auto is validate"
        )

        // Counts are not asserted — migrations may seed rows. The point is that each
        // mapped table is queryable, after Hibernate validated it at context startup.
        assertDoesNotThrow {
            deliveries.count()
            processedEvents.count()
        }
    }
}
