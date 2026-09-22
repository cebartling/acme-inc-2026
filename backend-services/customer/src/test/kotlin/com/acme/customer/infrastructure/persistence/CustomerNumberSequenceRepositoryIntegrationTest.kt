package com.acme.customer.infrastructure.persistence

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.YearMonth
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises [CustomerNumberSequenceRepository] against the real `next_customer_number`
 * function from V3 (PIN-277).
 *
 * `SchemaValidationTest` cannot reach this: Hibernate's `validate` checks tables and
 * columns, never functions, and `customer_number_sequences` has no `@Entity` at all.
 * `CreateCustomerUseCaseTest` mocks `nextSequence`, so until now nothing executed the
 * function. That is the same shape as the V9 defect, where three consent functions never
 * existed in any environment while 36 tests passed — except this one sits on the live
 * customer-creation path ([CreateCustomerUseCase] generates every customer number through
 * it), so a migration that silently failed to apply would break customer creation with no
 * failing test first.
 *
 * The test transaction is disabled because [concurrent sequence generation][
 * `concurrent calls should each return a unique sequence number`] runs on other threads,
 * which would not see an uncommitted test transaction and would not be rolled back with
 * it. Each test truncates the table instead.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CustomerNumberSequenceRepository::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class CustomerNumberSequenceRepositoryIntegrationTest {

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
    private lateinit var repository: CustomerNumberSequenceRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val yearMonth = YearMonth.of(2026, 1)

    @BeforeEach
    fun clearSequences() {
        jdbcTemplate.execute("TRUNCATE TABLE customer_number_sequences")
    }

    @Test
    fun `nextSequence should return 1 for a month with no sequence yet`() {
        // A migration that never applied would fail here, not in production.
        assertEquals(1, repository.nextSequence(yearMonth))
    }

    @Test
    fun `nextSequence should increment on each call`() {
        // When
        val sequences = (1..5).map { repository.nextSequence(yearMonth) }

        // Then
        assertEquals(listOf(1, 2, 3, 4, 5), sequences)
    }

    @Test
    fun `nextSequence should keep months independent`() {
        // Given
        repeat(3) { repository.nextSequence(yearMonth) }

        // When
        val otherMonth = repository.nextSequence(YearMonth.of(2026, 2))

        // Then
        assertEquals(1, otherMonth, "a new month must start its own sequence at 1")
        assertEquals(4, repository.nextSequence(yearMonth), "the original month must be unaffected")
    }

    @Test
    fun `getCurrentSequence should return 0 for a month with no sequence yet`() {
        assertEquals(0, repository.getCurrentSequence(yearMonth))
    }

    @Test
    fun `getCurrentSequence should return the current value without incrementing`() {
        // Given
        repeat(2) { repository.nextSequence(yearMonth) }

        // When / Then
        assertEquals(2, repository.getCurrentSequence(yearMonth))
        assertEquals(2, repository.getCurrentSequence(yearMonth), "reading must not increment")
        assertEquals(3, repository.nextSequence(yearMonth))
    }

    @Test
    fun `concurrent calls should each return a unique sequence number`() {
        // Atomicity is the entire reason this is a PostgreSQL function rather than a
        // read-then-write in Kotlin. A duplicate here means two customers would be issued
        // the same customer number.
        val callers = 50
        val pool = Executors.newFixedThreadPool(8)

        try {
            // When
            val futures = pool.invokeAll(
                (1..callers).map { Callable { repository.nextSequence(yearMonth) } }
            )
            val sequences = futures.map { it.get() }

            // Then
            assertEquals(callers, sequences.toSet().size, "every caller must get a distinct sequence")
            assertEquals((1..callers).toList(), sequences.sorted(), "sequences must be gapless")
            assertEquals(callers, repository.getCurrentSequence(yearMonth))
        } finally {
            pool.shutdown()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "sequence calls did not finish")
        }
    }
}
