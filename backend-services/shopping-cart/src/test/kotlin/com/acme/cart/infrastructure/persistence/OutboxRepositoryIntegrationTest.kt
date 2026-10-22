package com.acme.cart.infrastructure.persistence

import com.acme.cart.config.JacksonConfig
import com.acme.cart.domain.events.DomainEvent
import com.acme.cart.infrastructure.messaging.OutboxWriter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The outbox against real Postgres (PIN-384): the relay's query, and that an event commits
 * or rolls back with the caller's transaction. Tests manage their own transactions.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(OutboxWriter::class, JacksonConfig::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxRepositoryIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("acme_carts_outbox_test")
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

    private class TestEvent : DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "TestEvent",
        eventVersion = "1.0",
        timestamp = Instant.parse("2026-10-18T12:00:00Z"),
        aggregateId = UUID.randomUUID(),
        aggregateType = "Cart",
        correlationId = UUID.randomUUID()
    )

    @Autowired
    private lateinit var outbox: OutboxRepository

    @Autowired
    private lateinit var writer: OutboxWriter

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @AfterEach
    fun cleanUp() {
        outbox.deleteAll()
    }

    private fun message(createdAt: Instant, publishedAt: Instant? = null, retryCount: Int = 0) = OutboxMessage(
        aggregateId = UUID.randomUUID(),
        aggregateType = "Cart",
        eventType = "TestEvent",
        eventId = UUID.randomUUID(),
        topic = "cart.events",
        messageKey = UUID.randomUUID().toString(),
        payload = """{"eventType":"TestEvent"}""",
        createdAt = createdAt,
        publishedAt = publishedAt,
        retryCount = retryCount
    )

    @Test
    fun `findUnsent returns unsent messages in the order they were written, however often they failed`() {
        // created_at runs backwards, as with clock skew: the database sequence decides
        val base = Instant.parse("2026-10-21T12:00:00Z")
        val first = message(base.plusSeconds(3), retryCount = 50)
        val sent = message(base.plusSeconds(2), publishedAt = base)
        val second = message(base.plusSeconds(1))
        val third = message(base)
        listOf(first, sent, second, third).forEach { outbox.saveAndFlush(it) }

        assertEquals(listOf(first.id, second.id), outbox.findUnsent(PageRequest.of(0, 2)).map { it.id })
        assertEquals(
            listOf(first.id, second.id, third.id),
            outbox.findUnsent(PageRequest.of(0, 10)).map { it.id }
        )
    }

    @Test
    fun `an event written in a transaction that rolls back leaves no row`() {
        TransactionTemplate(transactionManager).executeWithoutResult { status ->
            writer.write(TestEvent())
            status.setRollbackOnly()
        }

        assertEquals(0, outbox.count())
    }

    @Test
    fun `an event written in a transaction that commits is saved unsent`() {
        val event = TestEvent()
        TransactionTemplate(transactionManager).executeWithoutResult { writer.write(event) }

        val saved = outbox.findAll().single()
        assertEquals(event.eventId, saved.eventId)
        assertTrue(saved.publishedAt == null)
    }

    @Test
    fun `writing outside a transaction fails fast`() {
        assertThrows<IllegalTransactionStateException> { writer.write(TestEvent()) }
        assertEquals(0, outbox.count())
    }
}
