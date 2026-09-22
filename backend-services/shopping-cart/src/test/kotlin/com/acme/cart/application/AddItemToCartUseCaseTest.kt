package com.acme.cart.application

import arrow.core.left
import arrow.core.right
import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartError
import com.acme.cart.domain.ProductSnapshot
import com.acme.cart.domain.VariantPricing
import com.acme.cart.domain.events.CartCreated
import com.acme.cart.domain.events.DomainEvent
import com.acme.cart.domain.events.ItemAddedToCart
import com.acme.cart.infrastructure.messaging.CartEventPublisher
import com.acme.cart.infrastructure.persistence.CartRepository
import com.acme.cart.infrastructure.product.ProductPricingClient
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AddItemToCartUseCaseTest {

    private val cartRepository = mockk<CartRepository>()
    private val pricingClient = mockk<ProductPricingClient>()
    private val eventPublisher = mockk<CartEventPublisher>()
    private val published = mutableListOf<DomainEvent>()

    private val useCase = AddItemToCartUseCase(
        cartRepository = cartRepository,
        pricingClient = pricingClient,
        eventPublisher = eventPublisher,
        transactionTemplate = TransactionTemplate(mockk<PlatformTransactionManager>(relaxed = true)),
        objectMapper = jacksonObjectMapper(),
        maxOrderQuantity = 5
    )

    private val variantId = UUID.randomUUID()
    private val snapshot = ProductSnapshot(
        productId = UUID.randomUUID(),
        name = "ACME Gaming Mouse Pro",
        sku = "ACME-GM-PRO-BLK",
        variantName = "Black",
        imageUrl = null
    )

    private fun command(quantity: Int = 2) = AddItemToCartCommand("sess-1", variantId, quantity, snapshot)

    @BeforeEach
    fun setUp() {
        every { pricingClient.getPricing(variantId) } returns VariantPricing(BigDecimal("69.99")).right()
        every { cartRepository.save(any()) } answers { firstArg() }
        every { eventPublisher.publish(capture(published)) } returns Unit
    }

    @Test
    fun `first add creates a cart and publishes CartCreated then ItemAddedToCart`() {
        every { cartRepository.findBySessionId("sess-1") } returns null

        val cart = useCase.execute(command()).getOrNull()!!

        assertEquals("sess-1", cart.sessionId)
        assertEquals(2, cart.itemCount)
        assertEquals(listOf("CartCreated", "ItemAddedToCart"), published.map { it.eventType })

        val created = assertIs<CartCreated>(published[0]).payload
        assertEquals(cart.id, created.cartId)
        assertEquals(null, created.customerId)

        val added = assertIs<ItemAddedToCart>(published[1]).payload
        assertEquals(cart.items.single().id, added.cartItemId)
        assertEquals("ACME-GM-PRO-BLK", added.sku)
        assertEquals(snapshot.productId, added.productId)
        assertEquals(2, added.quantity)
        assertEquals(BigDecimal("69.99"), added.unitPrice.amount)
        assertEquals("USD", added.unitPrice.currency)
        assertEquals("sess-1", added.sessionId)
    }

    @Test
    fun `adding to an existing cart publishes only ItemAddedToCart`() {
        every { cartRepository.findBySessionId("sess-1") } returns Cart(id = UUID.randomUUID(), sessionId = "sess-1")

        useCase.execute(command())

        assertEquals(listOf("ItemAddedToCart"), published.map { it.eventType })
    }

    @Test
    fun `the product snapshot is stored as JSON on the line`() {
        every { cartRepository.findBySessionId("sess-1") } returns null

        val cart = useCase.execute(command()).getOrNull()!!

        val stored = jacksonObjectMapper().readValue(cart.items.single().productSnapshot, ProductSnapshot::class.java)
        assertEquals(snapshot, stored)
    }

    @Test
    fun `a pricing failure saves nothing and publishes nothing`() {
        every { pricingClient.getPricing(variantId) } returns CartError.VariantNotFound(variantId).left()

        val result = useCase.execute(command())

        assertEquals(CartError.VariantNotFound(variantId), result.leftOrNull())
        verify(exactly = 0) { cartRepository.findBySessionId(any()) }
        verify(exactly = 0) { cartRepository.save(any()) }
        assertEquals(emptyList(), published)
    }

    @Test
    fun `exceeding the max quantity saves nothing and publishes nothing`() {
        every { cartRepository.findBySessionId("sess-1") } returns null

        val result = useCase.execute(command(quantity = 6))

        assertEquals(CartError.MaxQuantityExceeded(5), result.leftOrNull())
        verify(exactly = 0) { cartRepository.save(any()) }
        assertEquals(emptyList(), published)
    }

    @Test
    fun `a Kafka failure does not fail the add`() {
        every { cartRepository.findBySessionId("sess-1") } returns null
        every { eventPublisher.publish(any()) } throws IllegalStateException("broker down")

        val result = useCase.execute(command())

        assertEquals(2, result.getOrNull()!!.itemCount)
    }
}
