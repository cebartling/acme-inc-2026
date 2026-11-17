package com.acme.identity.infrastructure.projection

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

@Testcontainers
class UserSnapshotProjectorIntegrationTest {

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
    private lateinit var projector: UserSnapshotProjector

    private val now = Instant.parse("2026-11-16T12:00:00Z")

    @BeforeEach
    fun setUp() {
        mongoTemplate = MongoTemplate(MongoClients.create(mongo.connectionString), "projector_test")
        mongoTemplate.dropCollection(UserReadModelDocument.COLLECTION)
        projector = UserSnapshotProjector(mongoTemplate, objectMapper)
    }

    private fun message(
        userId: UUID,
        sourceEventId: UUID = UUID.randomUUID(),
        status: String = "PENDING_VERIFICATION",
        emailVerified: Boolean = false,
        lockedUntil: Instant? = null
    ): String = objectMapper.writeValueAsString(
        UserSnapshotMessage(
            sourceEventId = sourceEventId,
            userId = userId,
            snapshot = UserSnapshot(
                userId = userId,
                email = "user-$userId@example.com",
                firstName = "Una",
                lastName = "User",
                status = status,
                emailVerified = emailVerified,
                verifiedAt = if (emailVerified) now else null,
                lockedUntil = lockedUntil,
                registrationSource = "WEB",
                createdAt = now,
                updatedAt = now
            )
        )
    )

    private fun load(userId: UUID) = mongoTemplate.findOne(
        Query(Criteria.where("_id").`is`(userId.toString())),
        Document::class.java,
        UserReadModelDocument.COLLECTION
    )

    @Test
    fun `apply stores the snapshot under the user id with the email to look it up by`() {
        val userId = UUID.randomUUID()

        projector.apply(message(userId))

        val document = assertNotNull(load(userId))
        assertEquals("user-$userId@example.com", document.getString(UserReadModelDocument.EMAIL))
        val snapshot = document.get(UserReadModelDocument.SNAPSHOT, Document::class.java)
        assertEquals("PENDING_VERIFICATION", snapshot.getString("status"))
        assertEquals(false, snapshot.getBoolean("emailVerified"))
    }

    @Test
    fun `a later snapshot replaces the earlier one and redelivery changes nothing`() {
        val userId = UUID.randomUUID()
        val registered = message(userId)
        val activated = message(userId, status = "ACTIVE", emailVerified = true)

        projector.apply(registered)
        projector.apply(activated)
        projector.apply(activated)

        assertEquals(1, mongoTemplate.getCollection(UserReadModelDocument.COLLECTION).countDocuments())
        val snapshot = assertNotNull(load(userId)).get(UserReadModelDocument.SNAPSHOT, Document::class.java)
        assertEquals("ACTIVE", snapshot.getString("status"))
        assertEquals(true, snapshot.getBoolean("emailVerified"))
    }

    @Test
    fun `a lock is stored and the next snapshot clears it`() {
        val userId = UUID.randomUUID()

        projector.apply(message(userId, status = "ACTIVE", emailVerified = true, lockedUntil = now.plusSeconds(900)))
        assertNotNull(
            assertNotNull(load(userId)).get(UserReadModelDocument.SNAPSHOT, Document::class.java)["lockedUntil"]
        )

        projector.apply(message(userId, status = "ACTIVE", emailVerified = true))
        assertEquals(
            null,
            assertNotNull(load(userId)).get(UserReadModelDocument.SNAPSHOT, Document::class.java)["lockedUntil"]
        )
    }

    @Test
    fun `it keeps only the last applied event ids`() {
        val userId = UUID.randomUUID()
        val sourceIds = (1..UserReadModelDocument.MAX_APPLIED_EVENT_IDS + 5).map { UUID.randomUUID() }

        sourceIds.forEach { projector.apply(message(userId, sourceEventId = it)) }

        val applied = assertNotNull(load(userId)).getList(UserReadModelDocument.APPLIED_EVENT_IDS, String::class.java)
        assertEquals(sourceIds.takeLast(UserReadModelDocument.MAX_APPLIED_EVENT_IDS).map { it.toString() }, applied)
    }
}
