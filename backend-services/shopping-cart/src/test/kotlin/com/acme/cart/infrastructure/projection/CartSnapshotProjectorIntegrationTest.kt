package com.acme.cart.infrastructure.projection

import com.acme.cart.api.v1.CartItemResponse
import com.acme.cart.api.v1.CartResponse
import com.acme.cart.api.v1.CartSummaryResponse
import com.acme.cart.config.JacksonConfig
import com.acme.cart.domain.CartStatus
import com.acme.cart.domain.ProductSnapshot
import com.mongodb.client.MongoClients
import org.bson.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mongodb.MongoDBContainer
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers
class CartSnapshotProjectorIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val mongo = MongoDBContainer("mongo:7.0")
    }

    private val objectMapper = JacksonConfig().objectMapper()
    private lateinit var mongoTemplate: MongoTemplate
    private lateinit var projector: CartSnapshotProjector

    @BeforeEach
    fun setUp() {
        mongoTemplate = MongoTemplate(MongoClients.create(mongo.connectionString), "projector_test")
        mongoTemplate.dropCollection(CartReadModelDocument.COLLECTION)
        projector = CartSnapshotProjector(mongoTemplate, objectMapper)
    }

    private fun item(quantity: Int = 2) = CartItemResponse(
        id = UUID.randomUUID(),
        variantId = UUID.randomUUID(),
        quantity = quantity,
        unitPrice = BigDecimal("19.90"),
        lineTotal = BigDecimal("19.90").multiply(BigDecimal(quantity)),
        productSnapshot = ProductSnapshot(UUID.randomUUID(), "Anvil", "ANV-1", "Heavy", null)
    )

    private fun cart(
        cartId: UUID,
        items: List<CartItemResponse> = listOf(item()),
        status: CartStatus = CartStatus.ACTIVE
    ) = CartResponse(
        id = cartId,
        status = status,
        items = items,
        summary = CartSummaryResponse(items.sumOf { it.quantity }, items.sumOf { it.lineTotal }, "USD")
    )

    private fun message(
        cartId: UUID,
        cart: CartResponse? = cart(cartId),
        sourceEventId: UUID = UUID.randomUUID(),
        userId: UUID? = UUID.randomUUID(),
        sessionId: String? = null
    ): String = objectMapper.writeValueAsString(CartSnapshotMessage(sourceEventId, cartId, userId, sessionId, cart))

    private fun load(cartId: UUID) = mongoTemplate.findOne(
        Query(Criteria.where("_id").`is`(cartId.toString())),
        Document::class.java,
        CartReadModelDocument.COLLECTION
    )

    @Test
    fun `apply stores the cart under its id with the fields reads look up by`() {
        val cartId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val eventId = UUID.randomUUID()

        projector.apply(message(cartId, sourceEventId = eventId, userId = userId))

        val document = assertNotNull(load(cartId))
        assertEquals(userId.toString(), document.getString(CartReadModelDocument.USER_ID))
        assertNull(document.getString(CartReadModelDocument.SESSION_ID))
        assertEquals("ACTIVE", document.getString(CartReadModelDocument.STATUS))
        assertEquals(listOf(eventId.toString()), document.getList(CartReadModelDocument.APPLIED_EVENT_IDS, String::class.java))
        val stored = objectMapper.readValue(document.getString(CartReadModelDocument.CART_JSON), CartResponse::class.java)
        assertEquals(cartId, stored.id)
        assertEquals(1, stored.items.size)
        assertEquals("19.90", stored.items.single().unitPrice.toPlainString())
    }

    @Test
    fun `a guest cart is stored under its session id`() {
        val cartId = UUID.randomUUID()

        projector.apply(message(cartId, userId = null, sessionId = "guest-session"))

        val document = assertNotNull(load(cartId))
        assertEquals("guest-session", document.getString(CartReadModelDocument.SESSION_ID))
        assertNull(document.getString(CartReadModelDocument.USER_ID))
    }

    @Test
    fun `a later snapshot replaces the cart and adds its event id`() {
        val cartId = UUID.randomUUID()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        projector.apply(message(cartId, sourceEventId = first))

        projector.apply(message(cartId, cart(cartId, items = emptyList(), status = CartStatus.CHECKOUT), second))

        val document = assertNotNull(load(cartId))
        assertEquals("CHECKOUT", document.getString(CartReadModelDocument.STATUS))
        assertEquals(
            listOf(first.toString(), second.toString()),
            document.getList(CartReadModelDocument.APPLIED_EVENT_IDS, String::class.java)
        )
        val stored = objectMapper.readValue(document.getString(CartReadModelDocument.CART_JSON), CartResponse::class.java)
        assertTrue(stored.items.isEmpty())
    }

    @Test
    fun `applying the same snapshot twice leaves one document that still carries the event id`() {
        val cartId = UUID.randomUUID()
        val eventId = UUID.randomUUID()
        val json = message(cartId, sourceEventId = eventId)

        projector.apply(json)
        projector.apply(json)

        assertEquals(1, mongoTemplate.getCollection(CartReadModelDocument.COLLECTION).countDocuments())
        assertTrue(eventId.toString() in load(cartId)!!.getList(CartReadModelDocument.APPLIED_EVENT_IDS, String::class.java))
    }

    @Test
    fun `keeps only the last applied event ids`() {
        val cartId = UUID.randomUUID()
        val ids = (1..CartReadModelDocument.MAX_APPLIED_EVENT_IDS + 5).map { UUID.randomUUID() }

        ids.forEach { projector.apply(message(cartId, sourceEventId = it)) }

        val applied = load(cartId)!!.getList(CartReadModelDocument.APPLIED_EVENT_IDS, String::class.java)
        assertEquals(ids.takeLast(CartReadModelDocument.MAX_APPLIED_EVENT_IDS).map { it.toString() }, applied)
    }

    @Test
    fun `a snapshot Debezium delivers without the empty items array is stored as an empty cart`() {
        val cartId = UUID.randomUUID()
        val withoutItems = objectMapper.readTree(message(cartId, cart(cartId, items = emptyList())))
        (withoutItems["cart"] as com.fasterxml.jackson.databind.node.ObjectNode).remove("items")

        projector.apply(objectMapper.writeValueAsString(withoutItems))

        val stored = objectMapper.readValue(load(cartId)!!.getString(CartReadModelDocument.CART_JSON), CartResponse::class.java)
        assertTrue(stored.items.isEmpty())
        assertTrue(objectMapper.readTree(load(cartId)!!.getString(CartReadModelDocument.CART_JSON)).has("items"))
    }

    @Test
    fun `a snapshot with no cart deletes the document`() {
        val cartId = UUID.randomUUID()
        projector.apply(message(cartId))

        projector.apply(message(cartId, cart = null))

        assertNull(load(cartId))
    }

    @Test
    fun `a snapshot with no cart for an unknown cart is harmless`() {
        projector.apply(message(UUID.randomUUID(), cart = null))

        assertEquals(0, mongoTemplate.getCollection(CartReadModelDocument.COLLECTION).countDocuments())
    }
}
