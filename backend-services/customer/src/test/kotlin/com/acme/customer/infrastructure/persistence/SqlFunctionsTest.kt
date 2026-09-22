package com.acme.customer.infrastructure.persistence

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Timestamp
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the SQL functions this service's schema is expected to declare (PIN-277).
 *
 * Hibernate's `validate` never looks at functions, so nothing else in the suite would
 * notice one disappearing, changing signature, or coming back after being deliberately
 * removed. That is the V9 failure mode: three consent functions did not exist in any
 * environment while 36 tests passed.
 *
 * Two functions are expected, and they are guarded for different reasons:
 *
 *  - `next_customer_number` is called **only** from application code
 *    ([CustomerNumberSequenceRepository]), so no migration would fail if it went missing.
 *    Its behaviour is covered by [CustomerNumberSequenceRepositoryIntegrationTest]; what
 *    is pinned here is the signature the application calls it through.
 *  - `update_updated_at_column` is referenced by `CREATE TRIGGER ... EXECUTE FUNCTION` in
 *    V1, V2 and V7, so its absence already fails those migrations. It is included because
 *    "the migration would fail" is a guarantee about *creation*, not about the function
 *    still working after a later migration replaces it.
 *
 * `identity` and `notification` each declare `update_updated_at_column` and nothing else,
 * entirely trigger-referenced, so they are self-guarding in the same way and get no
 * equivalent test here.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class SqlFunctionsTest {

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
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `the schema should declare exactly the expected functions`() {
        // When — no migration creates an extension, so everything in public is ours
        val functions = jdbcTemplate.queryForList(
            """
            SELECT p.proname
            FROM pg_proc p
            JOIN pg_namespace n ON n.oid = p.pronamespace
            WHERE n.nspname = 'public'
            ORDER BY p.proname
            """.trimIndent(),
            String::class.java
        )

        // Then
        assertEquals(
            listOf("next_customer_number", "update_updated_at_column"),
            functions,
            "an unexpected function usually means V10 was reverted and the unused consent " +
                "functions (get_current_consents, get_consent_history, get_consent_version) " +
                "are back; a missing one means a migration that should declare it did not"
        )
    }

    @Test
    fun `next_customer_number should be callable through the signature the application uses`() {
        // CustomerNumberSequenceRepository calls `SELECT next_customer_number(?)` binding a
        // String, so a migration changing the parameter type would break it at runtime.
        val sequence = jdbcTemplate.queryForObject(
            "SELECT next_customer_number(?)",
            Int::class.java,
            "209912"
        )

        assertEquals(1, sequence, "a fresh year-month must start at 1")
    }

    @Test
    fun `update_updated_at_column should be callable and set updated_at`() {
        // Proves the function still works, not merely that it exists. A temp table keeps
        // this independent of the customers schema and disappears with the connection.
        jdbcTemplate.execute(
            """
            CREATE TEMP TABLE trigger_probe (
                id INTEGER PRIMARY KEY,
                updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
            )
            """.trimIndent()
        )
        jdbcTemplate.execute(
            """
            CREATE TRIGGER trigger_probe_updated_at
                BEFORE UPDATE ON trigger_probe
                FOR EACH ROW
                EXECUTE FUNCTION update_updated_at_column()
            """.trimIndent()
        )
        jdbcTemplate.update(
            "INSERT INTO trigger_probe (id, updated_at) VALUES (1, TIMESTAMP '2000-01-01 00:00:00+00')"
        )

        // When — the UPDATE does not touch updated_at; the trigger must
        jdbcTemplate.update("UPDATE trigger_probe SET id = 1 WHERE id = 1")

        // Then
        val updatedAt = jdbcTemplate.queryForObject(
            "SELECT updated_at FROM trigger_probe WHERE id = 1",
            Timestamp::class.java
        )!!
        assertTrue(
            updatedAt.toInstant().isAfter(java.time.Instant.parse("2020-01-01T00:00:00Z")),
            "the trigger function must have overwritten updated_at, but it is still $updatedAt"
        )
    }
}
