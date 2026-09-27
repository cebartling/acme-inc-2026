package com.acme.cart.application

import arrow.core.right
import com.acme.cart.api.v1.CartResponse
import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartOwner
import com.acme.cart.domain.ProductSnapshot
import com.acme.cart.domain.VariantPricing
import com.acme.cart.infrastructure.messaging.CartEventPublisher
import com.acme.cart.infrastructure.persistence.CartRepository
import com.acme.cart.infrastructure.product.ProductPricingClient
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.hibernate.LazyInitializationException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals

/**
 * With `spring.jpa.open-in-view: false` (PIN-296), no JPA session outlives a use case's
 * transaction, so the controller maps every cart to a response with no session open. These
 * tests do the same: the real use cases against real Postgres, then [CartResponse.from]
 * outside any transaction. A lazy read added to that path later fails here with
 * [LazyInitializationException] instead of in production.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CartResponsesOutsideSessionTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("acme_carts_test")
            .withUsername("test")
            .withPassword("test")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }

        private const val SESSION_PREFIX = "sess-no-osiv-"
    }

    @Autowired
    private lateinit var carts: CartRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val objectMapper = jacksonObjectMapper()
    private val pricingClient = mockk<ProductPricingClient>()
    private val eventPublisher = mockk<CartEventPublisher>(relaxed = true)
    private val pricing = VariantPricing(BigDecimal("69.99"))
    private val variantId = UUID.randomUUID()
    private val otherVariantId = UUID.randomUUID()
    private val userIds = mutableListOf<UUID>()

    private fun transactions() = TransactionTemplate(transactionManager)

    private val add by lazy {
        AddItemToCartUseCase(carts, pricingClient, eventPublisher, transactions(), objectMapper, 10, SimpleMeterRegistry())
    }
    private val update by lazy { UpdateCartItemQuantityUseCase(carts, pricingClient, eventPublisher, transactions(), 10) }
    private val remove by lazy { RemoveCartItemUseCase(carts, eventPublisher, transactions()) }
    private val merge by lazy { MergeCartsUseCase(carts, pricingClient, eventPublisher, transactions(), 10) }

    private fun session() = "$SESSION_PREFIX${UUID.randomUUID()}"

    private fun user() = UUID.randomUUID().also { userIds += it }

    private fun snapshot() = ProductSnapshot(UUID.randomUUID(), "ACME Gaming Mouse Pro", "ACME-GM-PRO-BLK", "Black", null)

    private fun addTo(owner: CartOwner, variant: UUID = variantId, quantity: Int = 2, newSession: Boolean = false): Cart =
        add.execute(AddItemToCartCommand(owner, variant, quantity, snapshot(), startedNewSession = newSession)).getOrNull()!!

    /** What the controller does with a use case's cart; throws if it needs an open session. */
    private fun respond(cart: Cart): CartResponse =
        CartResponse.from(cart) { objectMapper.readValue<ProductSnapshot>(it) }

    @BeforeEach
    fun setUp() {
        every { pricingClient.getPricing(any()) } returns pricing.right()
    }

    @AfterEach
    fun cleanUp() {
        transactions().execute {
            carts.findAll()
                .filter { cart -> cart.sessionId?.startsWith(SESSION_PREFIX) == true || cart.userId in userIds }
                .forEach(carts::delete)
        }
    }

    @Test
    fun `an add to a new cart maps to a response with no session open`() {
        val response = respond(addTo(CartOwner.Guest(session()), newSession = true))

        assertEquals(listOf(2), response.items.map { it.quantity })
    }

    @Test
    fun `an add to an existing cart maps to a response with no session open`() {
        val guest = CartOwner.Guest(session())
        addTo(guest)

        val response = respond(addTo(guest, variant = otherVariantId, quantity = 1))

        assertEquals(setOf(1, 2), response.items.map { it.quantity }.toSet())
    }

    @Test
    fun `an update maps to a response with no session open`() {
        val guest = CartOwner.Guest(session())
        val cart = addTo(guest)

        val updated = update.execute(UpdateCartItemQuantityCommand(guest, cart.id, cart.items.single().id, 3)).getOrNull()!!

        assertEquals(listOf(3), respond(updated).items.map { it.quantity })
    }

    @Test
    fun `a remove maps to a response with no session open`() {
        val guest = CartOwner.Guest(session())
        val cart = addTo(guest)

        val emptied = remove.execute(RemoveCartItemCommand(guest, cart.id, cart.items.single().id)).getOrNull()!!

        assertEquals(emptyList(), respond(emptied).items)
    }

    @Test
    fun `a merge into a new user cart maps to a response with no session open`() {
        val session = session()
        addTo(CartOwner.Guest(session))

        val merged = merge.execute(MergeCartsCommand(user(), session)).getOrNull()!!.cart!!

        assertEquals(listOf(2), respond(merged).items.map { it.quantity })
    }

    @Test
    fun `a merge into an existing user cart maps to a response with no session open`() {
        val session = session()
        val userId = user()
        addTo(CartOwner.Customer(userId), variant = otherVariantId, quantity = 1)
        addTo(CartOwner.Guest(session))

        val merged = merge.execute(MergeCartsCommand(userId, session)).getOrNull()!!.cart!!

        assertEquals(setOf(1, 2), respond(merged).items.map { it.quantity }.toSet())
    }

    @Test
    fun `GET current's lookup maps to a response with no session open`() {
        val guest = CartOwner.Guest(session())
        addTo(guest)

        // The controller calls the repository directly for GET /current, outside any use case.
        val current = carts.findActiveCart(guest)!!

        assertEquals(listOf(2), respond(current).items.map { it.quantity })
    }

    @Test
    fun `the guard has teeth - a cart loaded without its lines cannot be mapped outside a session`() {
        val cart = addTo(CartOwner.Guest(session()))

        // findById has no entity graph, so the lines stay lazy once its transaction ends.
        val unloaded = carts.findById(cart.id).get()

        assertThrows<LazyInitializationException> { respond(unloaded) }
    }
}
