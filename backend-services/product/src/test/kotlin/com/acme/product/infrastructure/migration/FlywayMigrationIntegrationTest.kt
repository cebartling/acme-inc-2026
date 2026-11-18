package com.acme.product.infrastructure.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Product's PostgreSQL schema is owned by the Flyway migrations, and the API no longer reads it
 * (PIN-550: reads come from MongoDB, which Debezium fills from these tables). Nothing else runs the
 * migrations against a real database, so a broken one would only show when the service starts.
 */
@Testcontainers
class FlywayMigrationIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("acme_products_test")
            .withUsername("test")
            .withPassword("test")

        // The tables the product-catalog Debezium connector captures (docker/debezium/connectors)
        private val CAPTURED_TABLES = listOf("products", "product_variants", "product_variant_images", "product_variant_tier_pricing")
    }

    private fun migrate() = Flyway.configure()
        .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        .locations("classpath:db/migration")
        .load()
        .migrate()

    @Test
    fun `every migration applies to an empty database and seeds the catalog`() {
        val result = migrate()

        assertTrue(result.success)
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            CAPTURED_TABLES.forEach { table ->
                connection.createStatement().executeQuery("SELECT count(*) FROM $table").use { rows ->
                    rows.next()
                    assertTrue(rows.getInt(1) > 0, "$table should exist and be seeded")
                }
            }
        }
    }

    @Test
    fun `running the migrations again changes nothing`() {
        migrate()

        assertEquals(0, migrate().migrationsExecuted)
    }
}
