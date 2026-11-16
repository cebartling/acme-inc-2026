package com.acme.cart.infrastructure.projection

import com.acme.cart.api.v1.CartResponse
import com.acme.cart.api.v1.CartSummaryResponse
import com.acme.cart.config.JacksonConfig
import com.acme.cart.domain.CartOwner
import com.acme.cart.domain.CartStatus
import com.mongodb.client.MongoClients
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mongodb.MongoDBContainer
import java.math.BigDecimal
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Testcontainers
class CartReadModelQueriesIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val mongo = MongoDBContainer("mongo:7.0")
    }

    private val objectMapper = JacksonConfig().objectMapper()
    private lateinit var projector: CartSnapshotProjector
    private lateinit var queries: CartReadModelQueries

    @BeforeEach
    fun setUp() {
        val mongoTemplate = MongoTemplate(MongoClients.create(mongo.connectionString), "queries_test")
        mongoTemplate.dropCollection(CartReadModelDocument.COLLECTION)
        projector = CartSnapshotProjector(mongoTemplate, objectMapper)
        queries = CartReadModelQueries(mongoTemplate, waitTimeoutMs = 600, pollIntervalMs = 10)
        queries.ensureIndexes()
    }

    private fun project(
        cartId: UUID = UUID.randomUUID(),
        userId: UUID? = null,
        sessionId: String? = null,
        status: CartStatus = CartStatus.ACTIVE,
        sourceEventId: UUID = UUID.randomUUID()
    ): UUID {
        val cart = CartResponse(cartId, status, emptyList(), CartSummaryResponse(0, BigDecimal.ZERO, "USD"))
        projector.apply(
            objectMapper.writeValueAsString(CartSnapshotMessage(sourceEventId, cartId, userId, sessionId, cart))
        )
        return cartId
    }

    private fun idOf(json: String?): UUID = UUID.fromString(objectMapper.readTree(assertNotNull(json))["id"].asText())

    @Test
    fun `finds a signed-in user's current cart by user id`() {
        val userId = UUID.randomUUID()
        val cartId = project(userId = userId)
        project(userId = UUID.randomUUID())

        assertEquals(cartId, idOf(queries.findCurrent(CartOwner.Customer(userId), readAfter = null)))
    }

    @Test
    fun `finds a guest's current cart by session id`() {
        val cartId = project(sessionId = "guest-1")
        project(sessionId = "guest-2")

        assertEquals(cartId, idOf(queries.findCurrent(CartOwner.Guest("guest-1"), readAfter = null)))
    }

    @Test
    fun `a cart in checkout is still current`() {
        val cartId = project(sessionId = "guest-1", status = CartStatus.CHECKOUT)

        assertEquals(cartId, idOf(queries.findCurrent(CartOwner.Guest("guest-1"), readAfter = null)))
    }

    @Test
    fun `final carts are not current, however recently they changed`() {
        listOf(CartStatus.MERGED, CartStatus.EXPIRED, CartStatus.CONVERTED).forEach {
            project(sessionId = "guest-1", status = it)
        }

        assertNull(queries.findCurrent(CartOwner.Guest("guest-1"), readAfter = null))
    }

    @Test
    fun `a final cart and a new current cart of one owner give the current one`() {
        project(userId = UUID.randomUUID(), status = CartStatus.CONVERTED)
        val userId = UUID.randomUUID()
        project(userId = userId, status = CartStatus.CONVERTED)
        val current = project(userId = userId)

        assertEquals(current, idOf(queries.findCurrent(CartOwner.Customer(userId), readAfter = null)))
    }

    @Test
    fun `a read with the event id of a change already applied returns at once`() {
        val eventId = UUID.randomUUID()
        val cartId = project(sessionId = "guest-1", sourceEventId = eventId)

        assertEquals(cartId, idOf(queries.findCurrent(CartOwner.Guest("guest-1"), readAfter = eventId)))
    }

    @Test
    fun `a read waits for the projector to apply the change it asks for`() {
        val eventId = UUID.randomUUID()
        val cartId = UUID.randomUUID()
        project(cartId = cartId, sessionId = "guest-1")
        val projecting = thread {
            Thread.sleep(150)
            project(cartId = cartId, sessionId = "guest-1", sourceEventId = eventId)
        }

        val json = queries.findCurrent(CartOwner.Guest("guest-1"), readAfter = eventId)

        projecting.join()
        assertEquals(cartId, idOf(json))
    }

    @Test
    fun `a read for a change that never arrives is behind, not stale`() {
        project(sessionId = "guest-1")
        val missing = UUID.randomUUID()

        val behind = assertFailsWith<ReadModelBehindException> {
            queries.findCurrent(CartOwner.Guest("guest-1"), readAfter = missing)
        }

        assertEquals(missing, behind.readAfter)
    }

    @Test
    fun `a change that left the owner with no current cart reads as no cart, not as behind`() {
        val userId = UUID.randomUUID()
        val converting = UUID.randomUUID()
        project(userId = userId, status = CartStatus.CONVERTED, sourceEventId = converting)

        assertNull(queries.findCurrent(CartOwner.Customer(userId), readAfter = converting))
    }
}
