package com.acme.notification.infrastructure.messaging

import com.acme.notification.application.eventhandlers.CustomerActivatedHandler
import com.acme.notification.infrastructure.messaging.dto.CustomerActivatedEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.kafka.support.Acknowledgment
import java.time.Instant
import java.util.UUID

class CustomerActivatedConsumerIntegrationTest {

    private lateinit var customerActivatedHandler: CustomerActivatedHandler
    private lateinit var objectMapper: ObjectMapper
    private lateinit var consumer: CustomerActivatedConsumer
    private lateinit var acknowledgment: Acknowledgment

    @BeforeEach
    fun setUp() {
        customerActivatedHandler = mockk(relaxed = true)
        objectMapper = ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
        acknowledgment = mockk(relaxed = true)

        consumer = CustomerActivatedConsumer(
            customerActivatedHandler = customerActivatedHandler,
            objectMapper = objectMapper,
            meterRegistry = SimpleMeterRegistry()
        )
    }

    @Test
    fun `should process CustomerActivated event successfully`() {
        val customerId = UUID.randomUUID()
        val eventId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()

        val eventJson = """
            {
                "eventId": "$eventId",
                "eventType": "CustomerActivated",
                "eventVersion": "1.0",
                "timestamp": "${Instant.now()}",
                "aggregateId": "$customerId",
                "aggregateType": "Customer",
                "correlationId": "$correlationId",
                "causationId": "${UUID.randomUUID()}",
                "payload": {
                    "customerId": "$customerId",
                    "activatedAt": "${Instant.now()}",
                    "emailVerified": true
                }
            }
        """.trimIndent()

        val record = ConsumerRecord<String, String>(
            "customer.events",
            0,
            100L,
            customerId.toString(),
            eventJson
        )

        consumer.consume(record, acknowledgment)

        verify { customerActivatedHandler.handle(any()) }
        verify { acknowledgment.acknowledge() }
    }

    @Test
    fun `should skip non-CustomerActivated events`() {
        val eventJson = """
            {
                "eventId": "${UUID.randomUUID()}",
                "eventType": "CustomerCreated",
                "eventVersion": "1.0",
                "timestamp": "${Instant.now()}",
                "aggregateId": "${UUID.randomUUID()}",
                "aggregateType": "Customer",
                "correlationId": "${UUID.randomUUID()}",
                "payload": {}
            }
        """.trimIndent()

        val record = ConsumerRecord<String, String>(
            "customer.events",
            0,
            100L,
            UUID.randomUUID().toString(),
            eventJson
        )

        consumer.consume(record, acknowledgment)

        verify(exactly = 0) { customerActivatedHandler.handle(any()) }
        verify { acknowledgment.acknowledge() }
    }

    @Test
    fun `should throw exception when handler fails`() {
        val customerId = UUID.randomUUID()
        val eventId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()

        val eventJson = """
            {
                "eventId": "$eventId",
                "eventType": "CustomerActivated",
                "eventVersion": "1.0",
                "timestamp": "${Instant.now()}",
                "aggregateId": "$customerId",
                "aggregateType": "Customer",
                "correlationId": "$correlationId",
                "causationId": "${UUID.randomUUID()}",
                "payload": {
                    "customerId": "$customerId",
                    "activatedAt": "${Instant.now()}",
                    "emailVerified": true
                }
            }
        """.trimIndent()

        val record = ConsumerRecord<String, String>(
            "customer.events",
            0,
            100L,
            customerId.toString(),
            eventJson
        )

        every { customerActivatedHandler.handle(any()) } throws RuntimeException("Processing failed")

        try {
            consumer.consume(record, acknowledgment)
        } catch (e: RuntimeException) {
            // Expected
        }

        verify(exactly = 0) { acknowledgment.acknowledge() }
    }

    @Test
    fun `should deserialize event with all payload fields`() {
        val customerId = UUID.randomUUID()
        val eventId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val causationId = UUID.randomUUID()
        val activatedAt = Instant.now()

        val eventJson = """
            {
                "eventId": "$eventId",
                "eventType": "CustomerActivated",
                "eventVersion": "1.0",
                "timestamp": "${Instant.now()}",
                "aggregateId": "$customerId",
                "aggregateType": "Customer",
                "correlationId": "$correlationId",
                "causationId": "$causationId",
                "payload": {
                    "customerId": "$customerId",
                    "activatedAt": "$activatedAt",
                    "emailVerified": true
                }
            }
        """.trimIndent()

        val record = ConsumerRecord<String, String>(
            "customer.events",
            0,
            100L,
            customerId.toString(),
            eventJson
        )

        val capturedEvent = slot<CustomerActivatedEvent>()
        every { customerActivatedHandler.handle(capture(capturedEvent)) } just runs

        consumer.consume(record, acknowledgment)

        val event = capturedEvent.captured
        assert(event.eventId == eventId)
        assert(event.eventType == "CustomerActivated")
        assert(event.correlationId == correlationId)
        assert(event.causationId == causationId)
        assert(event.payload.customerId == customerId)
        assert(event.payload.emailVerified)
    }
}
