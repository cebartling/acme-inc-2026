package com.acme.customer.infrastructure.projection

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class CustomerSnapshotJsonTest {

    private val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    /**
     * Debezium's outbox router drops empty arrays when it expands the payload column into JSON, so a
     * customer with no addresses or no consents arrives without those keys at all.
     */
    @Test
    fun `reads a snapshot from which the router dropped the empty lists`() {
        val customerId = UUID.randomUUID()
        val message = CustomerSnapshotMessage(
            sourceEventId = UUID.randomUUID(),
            customerId = customerId,
            snapshot = SnapshotFixtures.snapshot(customer = SnapshotFixtures.customer(customerId))
        )
        val tree = objectMapper.valueToTree<com.fasterxml.jackson.databind.node.ObjectNode>(message)
        (tree.get("snapshot") as com.fasterxml.jackson.databind.node.ObjectNode).remove(listOf("addresses", "consents"))

        val read = objectMapper.treeToValue(tree, CustomerSnapshotMessage::class.java)

        assertEquals(emptyList(), read.snapshot.addresses)
        assertEquals(emptyList(), read.snapshot.consents)
    }
}
