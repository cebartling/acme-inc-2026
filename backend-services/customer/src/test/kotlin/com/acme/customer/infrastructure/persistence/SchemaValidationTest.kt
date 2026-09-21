package com.acme.customer.infrastructure.persistence

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
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
 * The service runs `hibernate.ddl-auto: validate`, so an entity that disagrees with the
 * migrated schema is a hard startup failure. Every other test in this module mocks its
 * repositories, so that failure used to surface only when someone started the Docker
 * stack — never in CI.
 *
 * This test asserts nothing about behaviour on purpose. Flyway applying all migrations
 * and Hibernate validating all seven entities against the result *is* the assertion;
 * reaching the test body already means both succeeded. The `count()` calls below simply
 * name each mapped entity, so a newly added entity without a migration is an obvious
 * omission here.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class SchemaValidationTest {

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
    private lateinit var customers: CustomerRepository

    @Autowired
    private lateinit var preferences: CustomerPreferencesRepository

    @Autowired
    private lateinit var addresses: AddressRepository

    @Autowired
    private lateinit var consentRecords: ConsentRecordRepository

    @Autowired
    private lateinit var preferenceChangeLog: PreferenceChangeLogRepository

    @Autowired
    private lateinit var outbox: OutboxRepository

    @Autowired
    private lateinit var processedEvents: ProcessedEventRepository

    @Test
    fun `every entity validates against the Flyway-migrated schema`() {
        // Counts are not asserted — migrations may seed rows. The point is that each
        // mapped table is queryable, after Hibernate validated it at context startup.
        assertDoesNotThrow {
            customers.count()
            preferences.count()
            addresses.count()
            consentRecords.count()
            preferenceChangeLog.count()
            outbox.count()
            processedEvents.count()
        }
    }
}
