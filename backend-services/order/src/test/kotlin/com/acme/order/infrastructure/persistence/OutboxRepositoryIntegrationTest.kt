package com.acme.order.infrastructure.persistence

import com.acme.order.config.JacksonConfig
import com.acme.order.domain.events.DomainEvent
import com.acme.order.infrastructure.messaging.OutboxWriter
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
 * The outbox against real Postgres (PIN-368): the relay's query, and that an event commits
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
            .withDatabaseName("acme_orders_test")
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
        aggregateType = "Order",
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
        aggregateType = "Order",
        eventType = "TestEvent",
        eventId = UUID.randomUUID(),
        topic = "order.events",
        messageKey = UUID.randomUUID().toString(),
        payload = """{"eventType":"TestEvent"}""",
        createdAt = createdAt,
        publishedAt = publishedAt,
        retryCount = retryCount
    )

    @Test
    fun `findSendable returns unsent messages with attempts left, oldest first, one page at a time`() {
        val base = Instant.parse("2026-10-21T12:00:00Z")
        val oldest = message(base)
        val middle = message(base.plusSeconds(1), retryCount = 2)
        val newest = message(base.plusSeconds(2))
        val sent = message(base.minusSeconds(10), publishedAt = base)
        val usedUp = message(base.minusSeconds(5), retryCount = 3)
        outbox.saveAll(listOf(newest, sent, middle, usedUp, oldest))

        val firstPage = outbox.findSendable(3, PageRequest.of(0, 2))

        assertEquals(listOf(oldest.id, middle.id), firstPage.map { it.id })
        assertEquals(
            listOf(oldest.id, middle.id, newest.id),
            outbox.findSendable(3, PageRequest.of(0, 10)).map { it.id }
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
