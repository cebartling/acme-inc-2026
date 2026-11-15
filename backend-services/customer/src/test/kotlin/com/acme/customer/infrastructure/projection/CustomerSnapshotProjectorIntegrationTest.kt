package com.acme.customer.infrastructure.projection

import com.acme.customer.api.v1.dto.AddressResponse
import com.acme.customer.api.v1.dto.CustomerResponse
import com.acme.customer.api.v1.dto.ProfileCompletenessResponse
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.mongodb.client.MongoClients
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mongodb.MongoDBContainer
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Testcontainers
class CustomerSnapshotProjectorIntegrationTest {

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
    private lateinit var projector: CustomerSnapshotProjector

    @BeforeEach
    fun setUp() {
        mongoTemplate = MongoTemplate(MongoClients.create(mongo.connectionString), "projector_test")
        mongoTemplate.dropCollection(CustomerReadModelDocument.COLLECTION)
        projector = CustomerSnapshotProjector(mongoTemplate, objectMapper, SimpleMeterRegistry())
    }

    private fun message(
        customerId: UUID,
        sourceEventId: UUID = UUID.randomUUID(),
        withAddress: Boolean = false
    ): String {
        val customer = SnapshotFixtures.customer(customerId)
        val snapshot = CustomerSnapshot(
            customer = CustomerResponse.fromDomain(customer, SnapshotFixtures.preferences(customerId)),
            preferencesUpdatedAt = SnapshotFixtures.now,
            addresses = if (withAddress) {
                listOf(AddressResponse.fromEntity(SnapshotFixtures.address(customerId)))
            } else {
                emptyList()
            },
            consents = listOf(ConsentRecordSnapshot.fromDomain(SnapshotFixtures.consent(customerId))),
            completeness = ProfileCompletenessResponse.fromDomain(SnapshotFixtures.completeness(customerId))
        )
        return objectMapper.writeValueAsString(CustomerSnapshotMessage(sourceEventId, customerId, snapshot))
    }

    private fun load(customerId: UUID) = mongoTemplate.findOne(
        Query(Criteria.where("_id").`is`(customerId.toString())),
        org.bson.Document::class.java,
        CustomerReadModelDocument.COLLECTION
    )

    @Test
    fun `apply stores the snapshot under the customer id with the fields reads look up by`() {
        val customerId = UUID.randomUUID()

        projector.apply(message(customerId))

        val doc = assertNotNull(load(customerId))
        assertEquals("ada@example.com", doc.getString("email"))
        assertEquals("CUST-000001", doc.getString("customerNumber"))
        assertNotNull(doc.getString("userId"))
        val snapshot = doc.get("snapshot", org.bson.Document::class.java)
        assertEquals("Ada", snapshot.get("customer", org.bson.Document::class.java)
            .get("name", org.bson.Document::class.java).getString("firstName"))
    }

    @Test
    fun `apply replaces the earlier snapshot and remembers the source events`() {
        val customerId = UUID.randomUUID()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()

        projector.apply(message(customerId, first))
        projector.apply(message(customerId, second, withAddress = true))

        val doc = assertNotNull(load(customerId))
        val snapshot = doc.get("snapshot", org.bson.Document::class.java)
        assertEquals(1, snapshot.getList("addresses", org.bson.Document::class.java).size)
        assertEquals(listOf(first.toString(), second.toString()), doc.getList("appliedEventIds", String::class.java))
        assertEquals(1L, mongoTemplate.count(Query(), CustomerReadModelDocument.COLLECTION))
    }

    @Test
    fun `apply keeps only the most recent source event ids`() {
        val customerId = UUID.randomUUID()
        val ids = (1..CustomerReadModelDocument.MAX_APPLIED_EVENT_IDS + 5).map { UUID.randomUUID() }

        ids.forEach { projector.apply(message(customerId, it)) }

        val applied = assertNotNull(load(customerId)).getList("appliedEventIds", String::class.java)
        assertEquals(CustomerReadModelDocument.MAX_APPLIED_EVENT_IDS, applied.size)
        assertEquals(ids.last().toString(), applied.last())
        assertNull(applied.find { it == ids.first().toString() })
    }
}
