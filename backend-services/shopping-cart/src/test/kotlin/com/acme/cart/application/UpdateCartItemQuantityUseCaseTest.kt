package com.acme.cart.application

import arrow.core.left
import arrow.core.right
import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartError
import com.acme.cart.domain.PriceTier
import com.acme.cart.domain.VariantPricing
import com.acme.cart.domain.events.CartItemQuantityUpdated
import com.acme.cart.domain.events.DomainEvent
import com.acme.cart.infrastructure.messaging.CartEventPublisher
import com.acme.cart.infrastructure.persistence.CartRepository
import com.acme.cart.infrastructure.product.ProductPricingClient
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

class UpdateCartItemQuantityUseCaseTest {

    private val cartRepository = mockk<CartRepository>()
    private val pricingClient = mockk<ProductPricingClient>()
    private val eventPublisher = mockk<CartEventPublisher>()
    private val published = mutableListOf<DomainEvent>()

    private val useCase = UpdateCartItemQuantityUseCase(
        cartRepository = cartRepository,
        pricingClient = pricingClient,
        eventPublisher = eventPublisher,
        transactionTemplate = TransactionTemplate(mockk<PlatformTransactionManager>(relaxed = true)),
        maxOrderQuantity = 10
    )

    private val variantId = UUID.randomUUID()
    private val pricing = VariantPricing(BigDecimal("119.99"), listOf(PriceTier(3, BigDecimal("109.99"))))
    private val cart = Cart(id = UUID.randomUUID(), sessionId = "sess-1")
    private val item = cart.addItem(variantId, 2, pricing, "{}", 10).getOrNull()!!

    private fun command(quantity: Int, cartId: UUID = cart.id, itemId: UUID = item.id) =
        UpdateCartItemQuantityCommand("sess-1", cartId, itemId, quantity)

    @BeforeEach
    fun setUp() {
        every { cartRepository.findBySessionId("sess-1") } returns cart
        every { cartRepository.save(any()) } answers { firstArg() }
        every { pricingClient.getPricing(variantId) } returns pricing.right()
        every { eventPublisher.publish(capture(published)) } returns Unit
    }

    @Test
    fun `sets the quantity, reprices at the tier and publishes CartItemQuantityUpdated`() {
        val updated = useCase.execute(command(3)).getOrNull()!!

        assertEquals(3, updated.items.single().quantity)
        assertEquals(BigDecimal("109.99"), updated.items.single().unitPrice)
        val payload = assertIs<CartItemQuantityUpdated>(published.single()).payload
        assertEquals(2, payload.previousQuantity)
        assertEquals(3, payload.newQuantity)
        assertEquals("CUSTOMER_UPDATE", payload.reason)
        assertEquals(item.id, payload.cartItemId)
    }

    @Test
    fun `a cart id the session does not own is CartItemNotFound and nothing changes`() {
        val result = useCase.execute(command(3, cartId = UUID.randomUUID()))

        assertEquals(CartError.CartItemNotFound(item.id), result.leftOrNull())
        verify(exactly = 0) { pricingClient.getPricing(any()) }
        verify(exactly = 0) { cartRepository.save(any()) }
        assertEquals(emptyList(), published)
    }

    @Test
    fun `over max is refused without saving or publishing`() {
        assertEquals(CartError.MaxQuantityExceeded(10), useCase.execute(command(11)).leftOrNull())
        verify(exactly = 0) { cartRepository.save(any()) }
        assertEquals(emptyList(), published)
    }

    @Test
    fun `a pricing failure leaves the line unchanged`() {
        every { pricingClient.getPricing(variantId) } returns CartError.PricingUnavailable(variantId).left()

        assertEquals(CartError.PricingUnavailable(variantId), useCase.execute(command(3)).leftOrNull())
        assertEquals(2, item.quantity)
    }
}
