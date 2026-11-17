package com.acme.identity.infrastructure.persistence

import com.acme.identity.config.JacksonConfig
import com.acme.identity.domain.RegistrationSource
import com.acme.identity.domain.User
import com.acme.identity.domain.events.DomainEvent
import com.acme.identity.infrastructure.messaging.OutboxWriter
import com.acme.identity.infrastructure.projection.UserSnapshotFactory
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
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

/**
 * The outbox against real Postgres (PIN-548): that an event commits or rolls back with the
 * caller's transaction, and gets its own when there is none. Tests manage their own transactions.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(OutboxWriter::class, UserSnapshotFactory::class, JacksonConfig::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxRepositoryIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("acme_identity_test")
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

    private class TestEvent(aggregateId: UUID = UUID.randomUUID()) : DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "TestEvent",
        eventVersion = "1.0",
        timestamp = Instant.parse("2026-11-16T12:00:00Z"),
        aggregateId = aggregateId,
        aggregateType = "User",
        correlationId = UUID.randomUUID()
    )

    @Autowired
    private lateinit var outbox: OutboxRepository

    @Autowired
    private lateinit var writer: OutboxWriter

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @AfterEach
    fun cleanUp() {
        outbox.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `an event written in a transaction that rolls back leaves no row`() {
        TransactionTemplate(transactionManager).executeWithoutResult { status ->
            writer.write(TestEvent(), "identity.user.events")
            status.setRollbackOnly()
        }

        assertEquals(0, outbox.count())
    }

    @Test
    fun `an event written in a transaction that commits is saved with its topic, key and event id`() {
        val event = TestEvent()
        TransactionTemplate(transactionManager).executeWithoutResult { writer.write(event, "identity.user.events") }

        val saved = outbox.findAll().single()
        assertEquals(event.eventId, saved.eventId)
        assertEquals("identity.user.events", saved.topic)
        assertEquals(event.aggregateId.toString(), saved.messageKey)
        assertEquals("TestEvent", saved.eventType)
        assertEquals("User", saved.aggregateType)
    }

    @Test
    fun `an event written outside a transaction opens its own and is saved`() {
        val event = TestEvent()

        writer.write(event, "identity.session.events")

        assertEquals(event.eventId, outbox.findAll().single().eventId)
    }

    @Test
    fun `an event written with a snapshot also writes the user's state to the read model topic, without secrets`() {
        val user = userRepository.saveAndFlush(
            User(
                id = UUID.randomUUID(),
                email = "snapshot@example.com",
                passwordHash = "PASSWORD-HASH-SECRET",
                firstName = "Sam",
                lastName = "Snapshot",
                tosAcceptedAt = Instant.parse("2026-11-16T11:00:00Z"),
                registrationSource = RegistrationSource.WEB,
                totpSecret = "TOTP-SECRET",
                phoneNumber = "+15555550100"
            )
        )
        val event = TestEvent(aggregateId = user.id)

        TransactionTemplate(transactionManager).executeWithoutResult {
            writer.writeWithSnapshot(event, "identity.user.events")
        }

        val rows = outbox.findAll().associateBy { it.topic }
        assertEquals(setOf("identity.user.events", "identity.read-model"), rows.keys)
        val snapshotRow = rows.getValue("identity.read-model")
        assertEquals(user.id.toString(), snapshotRow.messageKey)
        assertEquals("UserSnapshot", snapshotRow.eventType)
        val message = objectMapper.readTree(snapshotRow.payload)
        assertEquals(event.eventId.toString(), message["sourceEventId"].asText())
        assertEquals("snapshot@example.com", message["snapshot"]["email"].asText())
        assertEquals("PENDING_VERIFICATION", message["snapshot"]["status"].asText())
        for (secret in listOf("PASSWORD-HASH-SECRET", "TOTP-SECRET", "+15555550100")) {
            assertEquals(false, snapshotRow.payload.contains(secret), "the snapshot must not contain $secret")
        }
    }
}
