package com.acme.notification.infrastructure.projection

import com.acme.notification.api.v1.dto.NotificationStatusResponse
import com.acme.notification.domain.NotificationStatus
import com.acme.notification.domain.NotificationType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.mongodb.client.MongoClients
import org.bson.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mongodb.MongoDBContainer
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers
class DeliverySnapshotProjectorIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val mongo = MongoDBContainer("mongo:7.0")
    }

    private val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    private lateinit var mongoTemplate: MongoTemplate
    private lateinit var projector: DeliverySnapshotProjector
    private lateinit var queries: NotificationReadModelQueries

    private val now = Instant.parse("2026-11-17T12:00:00Z")

    @BeforeEach
    fun setUp() {
        mongoTemplate = MongoTemplate(MongoClients.create(mongo.connectionString), "projector_test")
        mongoTemplate.dropCollection(NotificationReadModelDocument.COLLECTION)
        projector = DeliverySnapshotProjector(mongoTemplate, objectMapper)
        queries = NotificationReadModelQueries(mongoTemplate, objectMapper)
        queries.ensureIndexes()
    }

    private fun snapshot(
        notificationId: UUID,
        recipientId: UUID = UUID.randomUUID(),
        email: String = "recipient@example.com",
        status: NotificationStatus = NotificationStatus.SENT,
        createdAt: Instant = now
    ) = NotificationStatusResponse(
        notificationId = notificationId,
        notificationType = NotificationType.EMAIL_VERIFICATION,
        recipientId = recipientId,
        recipientEmail = email,
        status = status,
        attemptCount = 1,
        providerMessageId = "msg-1",
        sentAt = now,
        deliveredAt = null,
        bouncedAt = null,
        bounceReason = null,
        createdAt = createdAt
    )

    private fun message(snapshot: NotificationStatusResponse, sourceEventId: UUID = UUID.randomUUID()): String =
        objectMapper.writeValueAsString(
            DeliverySnapshotMessage(
                sourceEventId = sourceEventId,
                notificationId = snapshot.notificationId,
                snapshot = snapshot
            )
        )

    private fun load(notificationId: UUID) = mongoTemplate.findOne(
        Query(Criteria.where("_id").`is`(notificationId.toString())),
        Document::class.java,
        NotificationReadModelDocument.COLLECTION
    )

    @Test
    fun `a snapshot creates the delivery's document, readable by id`() {
        val id = UUID.randomUUID()

        projector.apply(message(snapshot(id)))

        val found = assertNotNull(queries.findById(id))
        assertEquals(id, found.notificationId)
        assertEquals(NotificationStatus.SENT, found.status)
        assertEquals("msg-1", found.providerMessageId)
        assertEquals(now, found.sentAt)
        assertNull(found.deliveredAt)
    }

    @Test
    fun `a later snapshot replaces the document and the same one again changes nothing`() {
        val id = UUID.randomUUID()
        val firstEvent = UUID.randomUUID()
        val secondEvent = UUID.randomUUID()
        val second = message(snapshot(id, status = NotificationStatus.FAILED), secondEvent)

        projector.apply(message(snapshot(id), firstEvent))
        projector.apply(second)
        projector.apply(second)

        assertEquals(NotificationStatus.FAILED, queries.findById(id)?.status)
        val applied = load(id)!!.getList(NotificationReadModelDocument.APPLIED_EVENT_IDS, String::class.java)
        assertTrue(applied.containsAll(listOf(firstEvent.toString(), secondEvent.toString())))
    }

    @Test
    fun `only the last 50 source event ids are kept`() {
        val id = UUID.randomUUID()

        repeat(NotificationReadModelDocument.MAX_APPLIED_EVENT_IDS + 5) { projector.apply(message(snapshot(id))) }

        val applied = load(id)!!.getList(NotificationReadModelDocument.APPLIED_EVENT_IDS, String::class.java)
        assertEquals(NotificationReadModelDocument.MAX_APPLIED_EVENT_IDS, applied.size)
    }

    @Test
    fun `deliveries are found by recipient id and by email, oldest first`() {
        val recipient = UUID.randomUUID()
        val older = UUID.randomUUID()
        val newer = UUID.randomUUID()
        projector.apply(message(snapshot(newer, recipient, "same@example.com", createdAt = now.plusSeconds(60))))
        projector.apply(message(snapshot(older, recipient, "same@example.com", createdAt = now)))
        projector.apply(message(snapshot(UUID.randomUUID(), email = "other@example.com")))

        assertEquals(listOf(older, newer), queries.findByRecipientId(recipient).map { it.notificationId })
        assertEquals(listOf(older, newer), queries.findByRecipientEmail("same@example.com").map { it.notificationId })
    }

    @Test
    fun `deliveries created within the same second are still returned oldest first`() {
        // As ISO strings, "...:00.500Z" sorts before "...:00Z"
        val recipient = UUID.randomUUID()
        val wholeSecond = UUID.randomUUID()
        val halfPast = UUID.randomUUID()
        projector.apply(message(snapshot(halfPast, recipient, createdAt = now.plusMillis(500))))
        projector.apply(message(snapshot(wholeSecond, recipient, createdAt = now)))

        assertEquals(listOf(wholeSecond, halfPast), queries.findByRecipientId(recipient).map { it.notificationId })
    }

    @Test
    fun `an unknown id or recipient finds nothing`() {
        assertNull(queries.findById(UUID.randomUUID()))
        assertEquals(emptyList(), queries.findByRecipientId(UUID.randomUUID()))
        assertEquals(emptyList(), queries.findByRecipientEmail("nobody@example.com"))
    }
}
