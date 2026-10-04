package com.acme.cart.application

import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartError
import com.acme.cart.domain.CartOwner
import com.acme.cart.domain.CartStatus
import com.acme.cart.domain.VariantPricing
import com.acme.cart.domain.events.CheckoutAbandoned
import com.acme.cart.domain.events.CheckoutSessionExpired
import com.acme.cart.domain.events.DomainEvent
import com.acme.cart.infrastructure.messaging.CartEventPublisher
import com.acme.cart.infrastructure.persistence.CartRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class AbandonCheckoutUseCaseTest {

    private val cartRepository = mockk<CartRepository>()
    private val eventPublisher = mockk<CartEventPublisher>()
    private val published = mutableListOf<DomainEvent>()
    private val meterRegistry = SimpleMeterRegistry()

    private val useCase = AbandonCheckoutUseCase(
        cartRepository = cartRepository,
        eventPublisher = eventPublisher,
        transactionTemplate = TransactionTemplate(mockk<PlatformTransactionManager>(relaxed = true)),
        checkoutSessionExpiry = CheckoutSessionExpiry(eventPublisher, meterRegistry)
    )

    private val cart = Cart(id = UUID.randomUUID(), sessionId = "sess-1").also {
        it.addItem(UUID.randomUUID(), 2, VariantPricing(BigDecimal("69.99")), "{}", 10)
    }

    @BeforeEach
    fun setUp() {
        every { cartRepository.findBySessionIdAndStatusIn("sess-1", CartStatus.CURRENT) } returns cart
        every { cartRepository.save(any()) } answers { firstArg() }
        every { eventPublisher.publish(capture(published)) } returns Unit
    }

    private fun abandon(cartId: UUID = cart.id) =
        useCase.execute(AbandonCheckoutCommand(CartOwner.Guest("sess-1"), cartId))

    @Test
    fun `unlocks a cart in checkout and publishes CheckoutAbandoned`() {
        val session = cart.startCheckout(Duration.ofMinutes(30)).getOrNull()!!

        val unlocked = abandon().getOrNull()!!

        assertSame(cart, unlocked)
        assertEquals(CartStatus.ACTIVE, unlocked.status)
        verify { cartRepository.save(cart) }
        val payload = assertIs<CheckoutAbandoned>(published.single()).payload
        assertEquals(cart.id, payload.cartId)
        assertEquals(session.id, payload.checkoutSessionId)
        assertEquals(1, payload.lineCount)
        assertEquals(2, payload.itemCount)
        assertEquals("sess-1", payload.sessionId)
    }

    @Test
    fun `leaving a session that already lapsed reports it as expired, not abandoned`() {
        val session = cart.startCheckout(Duration.ofMinutes(30), now = Instant.now().minusSeconds(3600)).getOrNull()!!

        val unlocked = abandon().getOrNull()!!

        assertEquals(CartStatus.ACTIVE, unlocked.status)
        val payload = assertIs<CheckoutSessionExpired>(published.single()).payload
        assertEquals(session.id, payload.checkoutSessionId)
        assertEquals(session.expiresAt, payload.expiredAt)
        assertEquals(1.0, meterRegistry.counter(CheckoutSessionExpiry.EXPIRED_METRIC).count())
    }

    @Test
    fun `a cart not in checkout is returned as is, with nothing saved or published`() {
        assertSame(cart, abandon().getOrNull())

        verify(exactly = 0) { cartRepository.save(any()) }
        assertEquals(emptyList(), published)
    }

    @Test
    fun `someone else's cart is not found`() {
        val other = UUID.randomUUID()

        assertEquals(CartError.CartNotFound(other), abandon(other).leftOrNull())
        assertEquals(emptyList(), published)
    }
}
