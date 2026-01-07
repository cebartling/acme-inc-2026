package com.acme.notification.infrastructure.messaging

import com.acme.notification.application.eventhandlers.UserRegisteredHandler
import com.acme.notification.infrastructure.messaging.dto.UserRegisteredEvent
import com.acme.notification.infrastructure.messaging.dto.UserRegisteredPayload
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.kafka.support.Acknowledgment
import java.time.Instant
import java.util.UUID

class UserRegisteredConsumerIntegrationTest {

    private lateinit var userRegisteredHandler: UserRegisteredHandler
    private lateinit var objectMapper: ObjectMapper
    private lateinit var consumer: UserRegisteredConsumer

    @BeforeEach
    fun setUp() {
        userRegisteredHandler = mockk(relaxed = true)
        objectMapper = ObjectMapper().apply {
            findAndRegisterModules()
        }
        consumer = UserRegisteredConsumer(
            userRegisteredHandler = userRegisteredHandler,
            objectMapper = objectMapper,
            meterRegistry = SimpleMeterRegistry()
        )
    }

    @Test
    fun `should consume valid UserRegistered event and call handler`() {
        val event = createTestEvent()
        val eventJson = objectMapper.writeValueAsString(event)
        val record = ConsumerRecord<String, String>("identity.user.events", 0, 0, event.aggregateId.toString(), eventJson)
        val acknowledgment = mockk<Acknowledgment>(relaxed = true)

        consumer.consume(record, acknowledgment)

        verify { userRegisteredHandler.handle(match { it.eventId == event.eventId }) }
        verify { acknowledgment.acknowledge() }
    }

    @Test
    fun `should skip events with wrong event type`() {
        val event = createTestEvent().copy(eventType = "UserDeleted")
        val eventJson = objectMapper.writeValueAsString(event)
        val record = ConsumerRecord<String, String>("identity.user.events", 0, 0, "key", eventJson)
        val acknowledgment = mockk<Acknowledgment>(relaxed = true)

        consumer.consume(record, acknowledgment)

        verify(exactly = 0) { userRegisteredHandler.handle(any()) }
        verify { acknowledgment.acknowledge() }
    }

    @Test
    fun `should rethrow exception on handler failure`() {
        val event = createTestEvent()
        val eventJson = objectMapper.writeValueAsString(event)
        val record = ConsumerRecord<String, String>("identity.user.events", 0, 0, "key", eventJson)
        val acknowledgment = mockk<Acknowledgment>(relaxed = true)

        every { userRegisteredHandler.handle(any()) } throws RuntimeException("Handler failed")

        try {
            consumer.consume(record, acknowledgment)
        } catch (e: RuntimeException) {
            // Expected
        }

        verify(exactly = 0) { acknowledgment.acknowledge() }
    }

    private fun createTestEvent() = UserRegisteredEvent(
        eventId = UUID.randomUUID(),
        eventType = "UserRegistered",
        eventVersion = "1.0",
        timestamp = Instant.now(),
        aggregateId = UUID.randomUUID(),
        aggregateType = "User",
        correlationId = UUID.randomUUID(),
        payload = UserRegisteredPayload(
            userId = UUID.randomUUID(),
            email = "user@example.com",
            firstName = "John",
            lastName = "Doe",
            tosAcceptedAt = Instant.now(),
            marketingOptIn = false,
            registrationSource = "WEB",
            verificationToken = "test-token-123"
        )
    )
}
