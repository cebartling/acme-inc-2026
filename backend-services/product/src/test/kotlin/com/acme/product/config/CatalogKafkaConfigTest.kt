package com.acme.product.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.mockk
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import org.springframework.kafka.listener.MessageListenerContainer
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How the catalog projector's listener treats a failing message (PIN-550): bad input is skipped
 * at once, because it never gets better; an infrastructure error such as MongoDB being down is
 * retried with a backoff.
 */
class CatalogKafkaConfigTest {

    private val handler = CatalogKafkaConfig("localhost:9092", "product-service", true).catalogErrorHandler()
    private val consumer = mockk<Consumer<String, String>>(relaxed = true)
    private val container = mockk<MessageListenerContainer>(relaxed = true)
    private val record = ConsumerRecord("product.public.products", 0, 7L, "key", "value")

    /** True when the handler gave up on the record (skipped it); false when it will be retried. */
    private fun skipped(error: Exception) = handler.handleOne(error, record, consumer, container)

    @Test
    fun `a message that is not valid JSON is skipped without retries`() {
        val parseError = assertFailsParsing()

        assertTrue(skipped(parseError))
    }

    @Test
    fun `a message the projector rejects as invalid is skipped without retries`() {
        assertTrue(skipped(IllegalArgumentException("The message for 'products' has no id")))
    }

    @Test
    fun `an infrastructure error is retried`() {
        assertFalse(skipped(IllegalStateException("MongoDB is unavailable")))
    }

    private fun assertFailsParsing(): Exception =
        try {
            ObjectMapper().readTree("not json")
            error("expected a parse error")
        } catch (e: com.fasterxml.jackson.core.JsonProcessingException) {
            e
        }
}
