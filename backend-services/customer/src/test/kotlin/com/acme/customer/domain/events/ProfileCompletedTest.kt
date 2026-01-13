package com.acme.customer.domain.events

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProfileCompletedTest {

    @Nested
    inner class Create {

        @Test
        fun `should create event with correct type`() {
            val customerId = UUID.randomUUID()
            val registeredAt = Instant.now().minus(7, ChronoUnit.DAYS)
            val correlationId = UUID.randomUUID()

            val event = ProfileCompleted.create(
                customerId = customerId,
                registeredAt = registeredAt,
                correlationId = correlationId
            )

            assertEquals(ProfileCompleted.EVENT_TYPE, event.eventType)
            assertEquals("ProfileCompleted", event.eventType)
        }

        @Test
        fun `should create event with correct version`() {
            val customerId = UUID.randomUUID()
            val registeredAt = Instant.now()
            val correlationId = UUID.randomUUID()

            val event = ProfileCompleted.create(
                customerId = customerId,
                registeredAt = registeredAt,
                correlationId = correlationId
            )

            assertEquals(ProfileCompleted.EVENT_VERSION, event.eventVersion)
            assertEquals("1.0", event.eventVersion)
        }

        @Test
        fun `should create event with correct aggregate type`() {
            val customerId = UUID.randomUUID()
            val registeredAt = Instant.now()
            val correlationId = UUID.randomUUID()

            val event = ProfileCompleted.create(
                customerId = customerId,
                registeredAt = registeredAt,
                correlationId = correlationId
            )

            assertEquals(ProfileCompleted.AGGREGATE_TYPE, event.aggregateType)
            assertEquals("Customer", event.aggregateType)
        }

        @Test
        fun `should set customer ID as aggregate ID`() {
            val customerId = UUID.randomUUID()
            val registeredAt = Instant.now()
            val correlationId = UUID.randomUUID()

            val event = ProfileCompleted.create(
                customerId = customerId,
                registeredAt = registeredAt,
                correlationId = correlationId
            )

            assertEquals(customerId, event.aggregateId)
        }

        @Test
        fun `should include customer ID in payload`() {
            val customerId = UUID.randomUUID()
            val registeredAt = Instant.now()
            val correlationId = UUID.randomUUID()

            val event = ProfileCompleted.create(
                customerId = customerId,
                registeredAt = registeredAt,
                correlationId = correlationId
            )

            assertEquals(customerId, event.payload.customerId)
        }

        @Test
        fun `should generate unique event ID`() {
            val customerId = UUID.randomUUID()
            val registeredAt = Instant.now()
            val correlationId = UUID.randomUUID()

            val event1 = ProfileCompleted.create(
                customerId = customerId,
                registeredAt = registeredAt,
                correlationId = correlationId
            )

            val event2 = ProfileCompleted.create(
                customerId = customerId,
                registeredAt = registeredAt,
                correlationId = correlationId
            )

            assertNotNull(event1.eventId)
            assertNotNull(event2.eventId)
            assertTrue(event1.eventId != event2.eventId)
        }

        @Test
        fun `should set correlation ID`() {
            val customerId = UUID.randomUUID()
            val registeredAt = Instant.now()
            val correlationId = UUID.randomUUID()

            val event = ProfileCompleted.create(
                customerId = customerId,
                registeredAt = registeredAt,
                correlationId = correlationId
            )

            assertEquals(correlationId, event.correlationId)
        }

        @Test
        fun `should set causation ID when provided`() {
            val customerId = UUID.randomUUID()
            val registeredAt = Instant.now()
            val correlationId = UUID.randomUUID()
            val causationId = UUID.randomUUID()

            val event = ProfileCompleted.create(
                customerId = customerId,
                registeredAt = registeredAt,
                correlationId = correlationId,
                causationId = causationId
            )

            assertEquals(causationId, event.causationId)
        }

        @Test
        fun `should have null causation ID when not provided`() {
            val customerId = UUID.randomUUID()
            val registeredAt = Instant.now()
            val correlationId = UUID.randomUUID()

            val event = ProfileCompleted.create(
                customerId = customerId,
                registeredAt = registeredAt,
                correlationId = correlationId
            )

            assertEquals(null, event.causationId)
        }

        @Test
        fun `should set completion timestamp in payload`() {
            val customerId = UUID.randomUUID()
            val registeredAt = Instant.now().minus(1, ChronoUnit.HOURS)
            val correlationId = UUID.randomUUID()

            val beforeCreate = Instant.now()
            val event = ProfileCompleted.create(
                customerId = customerId,
                registeredAt = registeredAt,
                correlationId = correlationId
            )
            val afterCreate = Instant.now()

            assertNotNull(event.payload.completedAt)
            assertTrue(event.payload.completedAt >= beforeCreate)
            assertTrue(event.payload.completedAt <= afterCreate)
        }

        @Test
        fun `should calculate time to complete as ISO 8601 duration`() {
            val customerId = UUID.randomUUID()
            val registeredAt = Instant.now().minus(2, ChronoUnit.HOURS)
            val correlationId = UUID.randomUUID()

            val event = ProfileCompleted.create(
                customerId = customerId,
                registeredAt = registeredAt,
                correlationId = correlationId
            )

            assertNotNull(event.payload.timeToComplete)
            // Duration should be approximately 2 hours in ISO 8601 format
            assertTrue(event.payload.timeToComplete.startsWith("PT"))
            assertTrue(event.payload.timeToComplete.contains("H") || event.payload.timeToComplete.contains("M"))
        }

        @Test
        fun `should format short time to complete correctly`() {
            val customerId = UUID.randomUUID()
            val registeredAt = Instant.now().minus(30, ChronoUnit.MINUTES)
            val correlationId = UUID.randomUUID()

            val event = ProfileCompleted.create(
                customerId = customerId,
                registeredAt = registeredAt,
                correlationId = correlationId
            )

            // Duration of 30 minutes should be parseable
            val duration = Duration.parse(event.payload.timeToComplete)
            assertTrue(duration.toMinutes() >= 29)
            assertTrue(duration.toMinutes() <= 31)
        }

        @Test
        fun `should format long time to complete correctly`() {
            val customerId = UUID.randomUUID()
            val registeredAt = Instant.now().minus(7, ChronoUnit.DAYS)
            val correlationId = UUID.randomUUID()

            val event = ProfileCompleted.create(
                customerId = customerId,
                registeredAt = registeredAt,
                correlationId = correlationId
            )

            // Duration of 7 days should be parseable
            val duration = Duration.parse(event.payload.timeToComplete)
            assertTrue(duration.toDays() >= 6)
            assertTrue(duration.toDays() <= 7)
        }

        @Test
        fun `should set event timestamp`() {
            val customerId = UUID.randomUUID()
            val registeredAt = Instant.now()
            val correlationId = UUID.randomUUID()

            val beforeCreate = Instant.now()
            val event = ProfileCompleted.create(
                customerId = customerId,
                registeredAt = registeredAt,
                correlationId = correlationId
            )
            val afterCreate = Instant.now()

            assertTrue(event.timestamp >= beforeCreate)
            assertTrue(event.timestamp <= afterCreate)
        }
    }

    @Nested
    inner class Topic {

        @Test
        fun `should have correct topic constant`() {
            assertEquals("customer.events", ProfileCompleted.TOPIC)
        }
    }

    @Nested
    inner class EventConstants {

        @Test
        fun `should have correct event type constant`() {
            assertEquals("ProfileCompleted", ProfileCompleted.EVENT_TYPE)
        }

        @Test
        fun `should have correct event version constant`() {
            assertEquals("1.0", ProfileCompleted.EVENT_VERSION)
        }

        @Test
        fun `should have correct aggregate type constant`() {
            assertEquals("Customer", ProfileCompleted.AGGREGATE_TYPE)
        }
    }
}
