package com.acme.cart.infrastructure.projection

import com.acme.cart.api.v1.CartResponse
import com.acme.cart.api.v1.CartSummaryResponse
import com.acme.cart.config.JacksonConfig
import com.acme.cart.config.KafkaConsumerConfig
import com.acme.cart.domain.CartStatus
import com.fasterxml.jackson.databind.ObjectMapper
import com.mongodb.client.MongoClients
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.bson.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.mongodb.MongoDBContainer
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

/**
 * The projector's listener, through real Kafka: a snapshot sent to `cart.read-model` reaches
 * MongoDB. [CartSnapshotProjectorIntegrationTest] calls `apply` directly, so it cannot tell
 * whether the listener starts at all (PIN-551: it did not, until the consumer was configured).
 */
@Testcontainers
class CartSnapshotKafkaProjectionIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val kafka = KafkaContainer("apache/kafka:3.8.0")

        @Container
        @JvmStatic
        val mongo = MongoDBContainer("mongo:7.0")
    }

    @Configuration
    @Import(KafkaConsumerConfig::class, CartSnapshotProjector::class)
    class Beans {
        @Bean
        fun mongoTemplate(): MongoTemplate = MongoTemplate(MongoClients.create(mongo.connectionString), "kafka_projection_test")

        @Bean
        fun objectMapper(): ObjectMapper = JacksonConfig().objectMapper()
    }

    private val objectMapper = JacksonConfig().objectMapper()
    private lateinit var context: AnnotationConfigApplicationContext
    private lateinit var mongoTemplate: MongoTemplate

    @BeforeEach
    fun setUp() {
        context = AnnotationConfigApplicationContext()
        TestPropertyValues.of(
            "spring.kafka.bootstrap-servers=${kafka.bootstrapServers}",
            "spring.kafka.consumer.group-id=shopping-cart-service-test"
        ).applyTo(context)
        context.register(Beans::class.java)
        context.refresh()
        mongoTemplate = context.getBean(MongoTemplate::class.java)
        mongoTemplate.dropCollection(CartReadModelDocument.COLLECTION)
    }

    @AfterEach
    fun tearDown() = context.close()

    private fun send(message: CartSnapshotMessage) {
        KafkaProducer<String, String>(
            mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java
            )
        ).use {
            it.send(ProducerRecord(CartSnapshotMessage.TOPIC, message.cartId.toString(), objectMapper.writeValueAsString(message))).get()
        }
    }

    private fun awaitDocument(cartId: UUID): Document {
        val deadline = System.nanoTime() + 30_000_000_000
        while (System.nanoTime() < deadline) {
            mongoTemplate.findOne(
                Query(Criteria.where("_id").`is`(cartId.toString())),
                Document::class.java,
                CartReadModelDocument.COLLECTION
            )?.let { return it }
            Thread.sleep(100)
        }
        fail("no read model document for cart $cartId within 30s: the projector's listener is not consuming")
    }

    @Test
    fun `a snapshot sent to cart read-model reaches the MongoDB read model`() {
        val cartId = UUID.randomUUID()
        val eventId = UUID.randomUUID()
        val cart = CartResponse(cartId, CartStatus.ACTIVE, emptyList(), CartSummaryResponse(0, BigDecimal.ZERO, "USD"))

        send(CartSnapshotMessage(eventId, cartId, userId = null, sessionId = "guest-1", cart = cart))

        val document = assertNotNull(awaitDocument(cartId))
        assertEquals("guest-1", document.getString(CartReadModelDocument.SESSION_ID))
        assertEquals(listOf(eventId.toString()), document.getList(CartReadModelDocument.APPLIED_EVENT_IDS, String::class.java))
    }
}
