package com.acme.notification.infrastructure.persistence

import com.acme.notification.config.JacksonConfig
import com.acme.notification.domain.NotificationDelivery
import com.acme.notification.domain.NotificationStatus
import com.acme.notification.domain.NotificationType
import com.acme.notification.domain.events.NotificationSent
import com.acme.notification.infrastructure.messaging.OutboxWriter
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
import java.util.UUID
import kotlin.test.assertEquals

/**
 * The outbox against real Postgres (PIN-549): that an event commits or rolls back with the
 * caller's transaction. Tests manage their own transactions.
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

    @Autowired
    private lateinit var outbox: OutboxRepository

    @Autowired
    private lateinit var deliveries: NotificationDeliveryRepository

    @Autowired
    private lateinit var writer: OutboxWriter

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private fun event() = NotificationSent.create(
        notificationId = UUID.randomUUID(),
        type = NotificationType.WELCOME,
        recipientId = UUID.randomUUID(),
        recipientEmail = "outbox@example.com",
        providerMessageId = "msg-1",
        status = NotificationStatus.SENT,
        correlationId = UUID.randomUUID()
    )

    @AfterEach
    fun cleanUp() {
        outbox.deleteAll()
    }

    @Test
    fun `an event written in a transaction that rolls back leaves no row`() {
        TransactionTemplate(transactionManager).executeWithoutResult { status ->
            writer.write(event(), NotificationSent.TOPIC)
            status.setRollbackOnly()
        }

        assertEquals(0, outbox.count())
    }

    @Test
    fun `an event written in a transaction that commits is saved with its topic, key, event id and payload`() {
        val event = event()
        TransactionTemplate(transactionManager).executeWithoutResult { writer.write(event, NotificationSent.TOPIC) }

        val saved = outbox.findAll().single()
        assertEquals(event.eventId, saved.eventId)
        assertEquals("notification.events", saved.topic)
        assertEquals(event.aggregateId.toString(), saved.messageKey)
        assertEquals("NotificationSent", saved.eventType)
        assertEquals("Notification", saved.aggregateType)
        val payload = objectMapper.readTree(saved.payload)
        assertEquals(event.eventId.toString(), payload["eventId"].asText())
        assertEquals("outbox@example.com", payload["payload"]["recipientEmail"].asText())
    }

    @Test
    fun `a delivery and its snapshot roll back together when the transaction fails`() {
        val delivery = delivery()

        assertThrows<IllegalStateException> {
            TransactionTemplate(transactionManager).executeWithoutResult {
                deliveries.save(delivery)
                writer.writeSnapshot(delivery, UUID.randomUUID())
                throw IllegalStateException("fail after both writes")
            }
        }

        assertEquals(0, deliveries.count())
        assertEquals(0, outbox.count())
    }

    @Test
    fun `a delivery and its snapshot commit together`() {
        val delivery = delivery()

        TransactionTemplate(transactionManager).executeWithoutResult {
            deliveries.save(delivery)
            writer.writeSnapshot(delivery, UUID.randomUUID())
        }

        assertEquals(1, deliveries.count())
        assertEquals(1, outbox.count())
        deliveries.deleteAll()
    }

    private fun delivery() = NotificationDelivery(
        id = UUID.randomUUID(),
        notificationType = NotificationType.WELCOME,
        recipientId = UUID.randomUUID(),
        recipientEmail = "atomic@example.com"
    )

    @Test
    fun `a snapshot is saved for the read model topic, keyed by notification id, with the delivery's state`() {
        val delivery = NotificationDelivery(
            id = UUID.randomUUID(),
            notificationType = NotificationType.EMAIL_VERIFICATION,
            recipientId = UUID.randomUUID(),
            recipientEmail = "snapshot@example.com"
        ).also { it.markAsSent("msg-9") }
        val sourceEventId = UUID.randomUUID()

        TransactionTemplate(transactionManager).executeWithoutResult { writer.writeSnapshot(delivery, sourceEventId) }

        val saved = outbox.findAll().single()
        assertEquals("notification.read-model", saved.topic)
        assertEquals(delivery.id.toString(), saved.messageKey)
        assertEquals("NotificationDeliverySnapshot", saved.eventType)
        val message = objectMapper.readTree(saved.payload)
        assertEquals(sourceEventId.toString(), message["sourceEventId"].asText())
        assertEquals(delivery.id.toString(), message["notificationId"].asText())
        assertEquals("snapshot@example.com", message["snapshot"]["recipientEmail"].asText())
        assertEquals("SENT", message["snapshot"]["status"].asText())
        assertEquals("msg-9", message["snapshot"]["providerMessageId"].asText())
    }
}
