package com.acme.product.infrastructure.projection

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.bson.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.kafka.support.Acknowledgment
import kotlin.test.assertFailsWith

/**
 * What the Kafka listener adds to [CatalogChangeProjector.apply] (PIN-550): which table a topic
 * names, and when a record is acknowledged. [CatalogChangeProjectorIntegrationTest] covers `apply`
 * against a real MongoDB; these use mocks, so a record is acknowledged only after it is written.
 */
class CatalogChangeProjectorConsumeTest {

    private lateinit var mongoTemplate: MongoTemplate
    private lateinit var rebuilder: CatalogViewRebuilder
    private lateinit var acknowledgment: Acknowledgment
    private lateinit var projector: CatalogChangeProjector

    @BeforeEach
    fun setUp() {
        mongoTemplate = mockk(relaxed = true)
        rebuilder = mockk(relaxed = true)
        acknowledgment = mockk(relaxed = true)
        projector = CatalogChangeProjector(mongoTemplate, ObjectMapper(), rebuilder)
    }

    private fun record(topic: String, value: String?) = ConsumerRecord<String, String?>(topic, 0, 7L, "key", value)

    private val variantRow = """{"id":"v1","product_id":"p1","__op":"c","__deleted":"false"}"""

    @Test
    fun `the table is the last part of the topic name, and the record is acknowledged once it is written`() {
        projector.consume(record("product.public.product_variants", variantRow), acknowledgment)

        verify(exactly = 1) { mongoTemplate.save(any<Document>(), CatalogReadModel.VARIANTS) }
        verify(exactly = 1) { rebuilder.rebuild("p1") }
        verify(exactly = 1) { acknowledgment.acknowledge() }
    }

    @Test
    fun `a record with no value is acknowledged without being written`() {
        projector.consume(record("product.public.products", null), acknowledgment)

        verify(exactly = 0) { mongoTemplate.save(any<Document>(), any<String>()) }
        verify(exactly = 1) { acknowledgment.acknowledge() }
    }

    @Test
    fun `a record that is not valid JSON is not acknowledged, and the failure is rethrown for the container`() {
        assertFailsWith<Exception> { projector.consume(record("product.public.products", "not json"), acknowledgment) }

        verify(exactly = 0) { acknowledgment.acknowledge() }
        verify(exactly = 0) { mongoTemplate.save(any<Document>(), any<String>()) }
    }

    @Test
    fun `a record from a topic that is not a catalog table is not acknowledged`() {
        assertFailsWith<IllegalArgumentException> { projector.consume(record("product.public.orders", variantRow), acknowledgment) }

        verify(exactly = 0) { acknowledgment.acknowledge() }
    }

    @Test
    fun `a record is not acknowledged when writing it fails`() {
        io.mockk.every { mongoTemplate.save(any<Document>(), any<String>()) } throws IllegalStateException("MongoDB is unavailable")

        assertFailsWith<IllegalStateException> { projector.consume(record("product.public.product_variants", variantRow), acknowledgment) }

        verify(exactly = 0) { acknowledgment.acknowledge() }
    }
}
