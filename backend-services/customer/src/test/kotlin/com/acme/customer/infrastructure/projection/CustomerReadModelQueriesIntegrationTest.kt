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
import org.junit.jupiter.api.assertThrows
import org.springframework.data.mongodb.core.MongoTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mongodb.MongoDBContainer
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers
class CustomerReadModelQueriesIntegrationTest {

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
    private lateinit var queries: CustomerReadModelQueries

    @BeforeEach
    fun setUp() {
        mongoTemplate = MongoTemplate(MongoClients.create(mongo.connectionString), "queries_test")
        mongoTemplate.dropCollection(CustomerReadModelDocument.COLLECTION)
        projector = CustomerSnapshotProjector(mongoTemplate, objectMapper, SimpleMeterRegistry())
        queries = CustomerReadModelQueries(mongoTemplate, objectMapper, waitTimeoutMs = 500, pollIntervalMs = 10)
        queries.ensureIndexes()
    }

    private fun project(customerId: UUID, sourceEventId: UUID = UUID.randomUUID()): CustomerSnapshot {
        val customer = SnapshotFixtures.customer(customerId)
        val snapshot = CustomerSnapshot(
            customer = CustomerResponse.fromDomain(customer, SnapshotFixtures.preferences(customerId)),
            preferencesUpdatedAt = SnapshotFixtures.now,
            addresses = listOf(AddressResponse.fromEntity(SnapshotFixtures.address(customerId))),
            consents = listOf(ConsentRecordSnapshot.fromDomain(SnapshotFixtures.consent(customerId))),
            completeness = ProfileCompletenessResponse.fromDomain(SnapshotFixtures.completeness(customerId))
        )
        projector.apply(objectMapper.writeValueAsString(CustomerSnapshotMessage(sourceEventId, customerId, snapshot)))
        return snapshot
    }

    @Test
    fun `findById returns the snapshot as it was projected, round-tripped through MongoDB`() {
        val customerId = UUID.randomUUID()
        val projected = project(customerId)

        val found = assertNotNull(queries.findById(customerId))

        assertEquals(projected.customer, found.customer)
        assertEquals(projected.addresses, found.addresses)
        assertEquals(true, found.addresses.single().isDefault)
        assertEquals(projected.consents, found.consents)
        assertEquals(projected.completeness, found.completeness)
        assertEquals(projected.preferencesUpdatedAt, found.preferencesUpdatedAt)
    }

    @Test
    fun `finds by user id, customer number and email`() {
        val customerId = UUID.randomUUID()
        val projected = project(customerId)

        assertEquals(customerId.toString(), queries.findByUserId(UUID.fromString(projected.customer.userId))?.customer?.customerId)
        assertEquals(customerId.toString(), queries.findByCustomerNumber("CUST-000001")?.customer?.customerId)
        assertEquals(customerId.toString(), queries.findByEmail("ada@example.com")?.customer?.customerId)
    }

    @Test
    fun `returns null when there is no such customer and no wait was asked for`() {
        assertNull(queries.findById(UUID.randomUUID()))
        assertNull(queries.findByEmail("nobody@example.com"))
    }

    @Test
    fun `waits for the read model to apply the event a write returned`() {
        val customerId = UUID.randomUUID()
        val writeEvent = UUID.randomUUID()
        project(customerId)
        val projectLater = thread {
            Thread.sleep(100)
            project(customerId, writeEvent)
        }

        val found = queries.findById(customerId, readAfter = writeEvent)

        projectLater.join()
        assertNotNull(found)
    }

    @Test
    fun `waits for a customer that is not in the read model yet`() {
        val customerId = UUID.randomUUID()
        val writeEvent = UUID.randomUUID()
        val projectLater = thread {
            Thread.sleep(100)
            project(customerId, writeEvent)
        }

        val found = queries.findById(customerId, readAfter = writeEvent)

        projectLater.join()
        assertNotNull(found)
    }

    @Test
    fun `gives up with ReadModelBehindException when the event never arrives`() {
        val customerId = UUID.randomUUID()
        project(customerId)
        val neverApplied = UUID.randomUUID()

        val error = assertThrows<ReadModelBehindException> { queries.findById(customerId, readAfter = neverApplied) }

        assertTrue(error.message!!.contains(neverApplied.toString()))
    }

    @Test
    fun `ensureIndexes does not fail when run twice`() {
        queries.ensureIndexes()
        queries.ensureIndexes()
    }
}
