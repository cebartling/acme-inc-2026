package com.acme.cart.infrastructure.persistence

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import kotlin.test.assertTrue

/**
 * V3's backfill (PIN-287): existing carts must start their idle clock at the migration, not
 * at `updated_at`. Since PIN-268 a view extends the cookie without changing `updated_at`, so
 * backfilling from it could expire a cart whose cookie is still valid.
 */
@Testcontainers
class CartExpiryMigrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("acme_carts_migration_test")
            .withUsername("test")
            .withPassword("test")
    }

    private fun flyway(target: String) = Flyway.configure()
        .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        .locations("classpath:db/migration")
        .target(target)
        .load()

    @Test
    fun `existing carts start their idle clock at the migration, not at their last change`() {
        flyway("2").migrate()
        val lastChanged = Instant.now().minus(Duration.ofDays(25))
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { db ->
            db.prepareStatement(
                "insert into carts (id, session_id, status, created_at, updated_at) values (gen_random_uuid(), 'sess-old', 'ACTIVE', ?, ?)"
            ).apply {
                setTimestamp(1, Timestamp.from(lastChanged))
                setTimestamp(2, Timestamp.from(lastChanged))
            }.executeUpdate()
        }

        val migratedAt = Instant.now()
        flyway("latest").migrate()

        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { db ->
            val rs = db.createStatement().executeQuery("select last_active_at from carts where session_id = 'sess-old'")
            rs.next()
            val lastActive = rs.getTimestamp(1).toInstant()
            assertTrue(
                lastActive >= migratedAt.minusSeconds(5),
                "last_active_at was $lastActive; it must be the migration time, not updated_at ($lastChanged)"
            )
        }
    }
}
