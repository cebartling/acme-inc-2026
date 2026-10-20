package com.acme.cart.application

import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartError
import com.acme.cart.domain.CartOwner
import com.acme.cart.domain.CartStatus
import com.acme.cart.domain.VariantPricing
import com.acme.cart.domain.events.CartConvertedToOrder
import com.acme.cart.domain.events.DomainEvent
import com.acme.cart.infrastructure.messaging.CartEventPublisher
import com.acme.cart.infrastructure.persistence.CartRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConvertCartUseCaseTest {

    private val cartRepository = mockk<CartRepository>()
    private val eventPublisher = mockk<CartEventPublisher>()
    private val published = mutableListOf<DomainEvent>()

    private val useCase = ConvertCartUseCase(
        cartRepository = cartRepository,
        eventPublisher = eventPublisher,
        transactionTemplate = TransactionTemplate(mockk<PlatformTransactionManager>(relaxed = true))
    )

    private val cart = Cart(id = UUID.randomUUID(), sessionId = "sess-1").also {
        it.addItem(UUID.randomUUID(), 2, VariantPricing(BigDecimal("69.99")), "{}", 10)
    }
    private val orderId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        every { cartRepository.findByIdAndSessionId(any(), any()) } returns null
        every { cartRepository.findByIdAndSessionId(cart.id, "sess-1") } returns cart
        every { cartRepository.save(any()) } answers { firstArg() }
        every { eventPublisher.publish(capture(published)) } returns Unit
    }

    private fun convert(checkoutSessionId: UUID, cartId: UUID = cart.id, order: UUID = orderId) =
        useCase.execute(ConvertCartCommand(CartOwner.Guest("sess-1"), cartId, order, checkoutSessionId))

    @Test
    fun `converts a cart in checkout and publishes CartConvertedToOrder`() {
        val session = cart.startCheckout(Duration.ofMinutes(30)).getOrNull()!!

        val converted = convert(session.id).getOrNull()!!

        assertEquals(CartStatus.CONVERTED, converted.cart.status)
        assertEquals(orderId, converted.conversion.orderId)
        verify { cartRepository.save(cart) }
        val payload = assertIs<CartConvertedToOrder>(published.single()).payload
        assertEquals(cart.id, payload.cartId)
        assertEquals(orderId, payload.orderId)
        assertEquals(session.id, payload.checkoutSessionId)
        assertEquals(1, payload.lineCount)
        assertEquals(2, payload.itemCount)
        assertEquals("sess-1", payload.sessionId)
    }

    @Test
    fun `converting again into the same order saves and publishes nothing`() {
        val session = cart.startCheckout(Duration.ofMinutes(30)).getOrNull()!!
        val first = convert(session.id).getOrNull()!!
        published.clear()

        val again = convert(session.id).getOrNull()!!

        assertTrue(again.conversion.repeated)
        assertEquals(first.conversion.convertedAt, again.conversion.convertedAt)
        verify(exactly = 1) { cartRepository.save(cart) }
        assertEquals(emptyList(), published)
    }

    @Test
    fun `a refused conversion saves and publishes nothing`() {
        val other = UUID.randomUUID()

        assertEquals(CartError.CheckoutSessionNotFound(other), convert(other).leftOrNull())
        verify(exactly = 0) { cartRepository.save(any()) }
        assertEquals(emptyList(), published)
    }

    @Test
    fun `someone else's cart is not found`() {
        val other = UUID.randomUUID()

        assertEquals(CartError.CartNotFound(other), convert(UUID.randomUUID(), cartId = other).leftOrNull())
        assertEquals(emptyList(), published)
    }
}
