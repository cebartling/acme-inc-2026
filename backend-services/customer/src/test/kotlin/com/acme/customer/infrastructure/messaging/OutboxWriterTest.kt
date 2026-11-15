package com.acme.customer.infrastructure.messaging

import com.acme.customer.domain.events.PreferencesUpdated
import com.acme.customer.infrastructure.persistence.OutboxMessage
import com.acme.customer.infrastructure.persistence.OutboxRepository
import com.acme.customer.infrastructure.projection.CustomerSnapshot
import com.acme.customer.infrastructure.projection.CustomerSnapshotFactory
import com.acme.customer.infrastructure.projection.SnapshotFixtures
import com.acme.customer.api.v1.dto.CustomerResponse
import com.acme.customer.api.v1.dto.ProfileCompletenessResponse
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verifyOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class OutboxWriterTest {

    private val objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())
    private lateinit var outboxRepository: OutboxRepository
    private lateinit var snapshotFactory: CustomerSnapshotFactory
    private lateinit var writer: OutboxWriter
    private val saved = mutableListOf<OutboxMessage>()

    @BeforeEach
    fun setUp() {
        saved.clear()
        outboxRepository = mockk()
        snapshotFactory = mockk()
        every { outboxRepository.save(capture(saved)) } answers { firstArg() }
        writer = OutboxWriter(outboxRepository, objectMapper, snapshotFactory)
    }

    private fun event(customerId: UUID) = PreferencesUpdated.create(
        customerId = customerId,
        changedPreferences = emptyMap(),
        correlationId = UUID.randomUUID()
    )

    private fun snapshotOf(customerId: UUID): CustomerSnapshot {
        val customer = SnapshotFixtures.customer(customerId)
        return CustomerSnapshot(
            customer = CustomerResponse.fromDomain(customer, SnapshotFixtures.preferences(customerId)),
            preferencesUpdatedAt = SnapshotFixtures.now,
            addresses = emptyList(),
            consents = emptyList(),
            completeness = ProfileCompletenessResponse.fromDomain(SnapshotFixtures.completeness(customerId))
        )
    }

    @Test
    fun `write saves the event row first, unchanged`() {
        val customerId = UUID.randomUUID()
        val event = event(customerId)
        every { snapshotFactory.build(customerId) } returns snapshotOf(customerId)

        writer.write(event, PreferencesUpdated.TOPIC)

        val row = saved.first()
        assertEquals("customer.events", row.topic)
        assertEquals(event.eventId, row.eventId)
        assertEquals(customerId.toString(), row.messageKey)
        assertEquals(objectMapper.writeValueAsString(event), row.payload)
    }

    @Test
    fun `write adds a snapshot row for the read model after the event row`() {
        val customerId = UUID.randomUUID()
        val event = event(customerId)
        every { snapshotFactory.build(customerId) } returns snapshotOf(customerId)

        writer.write(event, PreferencesUpdated.TOPIC)

        assertEquals(2, saved.size)
        val snapshotRow = saved[1]
        assertEquals("customer.read-model", snapshotRow.topic)
        assertEquals(customerId.toString(), snapshotRow.messageKey)
        assertEquals(customerId, snapshotRow.aggregateId)
        assertEquals("CustomerSnapshot", snapshotRow.eventType)
        assertNotEquals(event.eventId, snapshotRow.eventId)

        val payload = objectMapper.readTree(snapshotRow.payload)
        assertEquals(event.eventId.toString(), payload["sourceEventId"].asText())
        assertEquals(customerId.toString(), payload["customerId"].asText())
        assertEquals(customerId.toString(), payload["snapshot"]["customer"]["customerId"].asText())
    }

    @Test
    fun `write keeps the event row in customer events out of the snapshot`() {
        val customerId = UUID.randomUUID()
        every { snapshotFactory.build(customerId) } returns snapshotOf(customerId)

        writer.write(event(customerId), PreferencesUpdated.TOPIC)

        // The event payload must not carry the snapshot (it holds PII that notification must not see).
        assertEquals(false, objectMapper.readTree(saved.first().payload).has("snapshot"))
        verifyOrder {
            outboxRepository.save(match { it.topic == "customer.events" })
            outboxRepository.save(match { it.topic == "customer.read-model" })
        }
    }
}
