package com.acme.cart.application

import arrow.core.left
import arrow.core.right
import com.acme.cart.domain.AvailabilityIssue
import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartError
import com.acme.cart.domain.CartOwner
import com.acme.cart.domain.CartStatus
import com.acme.cart.domain.UnavailableLine
import com.acme.cart.domain.VariantPricing
import com.acme.cart.domain.events.CheckoutInitiated
import com.acme.cart.domain.events.DomainEvent
import com.acme.cart.domain.events.Money
import com.acme.cart.infrastructure.messaging.CartEventPublisher
import com.acme.cart.infrastructure.persistence.CartRepository
import com.acme.cart.infrastructure.product.ProductAvailabilityClient
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StartCheckoutUseCaseTest {

    private val cartRepository = mockk<CartRepository>()
    private val availabilityClient = mockk<ProductAvailabilityClient>()
    private val eventPublisher = mockk<CartEventPublisher>()
    private val published = mutableListOf<DomainEvent>()

    private val useCase = StartCheckoutUseCase(
        cartRepository = cartRepository,
        availabilityClient = availabilityClient,
        eventPublisher = eventPublisher,
        transactionTemplate = TransactionTemplate(mockk<PlatformTransactionManager>(relaxed = true)),
        objectMapper = jacksonObjectMapper()
    )

    private val owner = CartOwner.Guest("sess-1")
    private val mouse = UUID.randomUUID()
    private val pad = UUID.randomUUID()
    private val cart = Cart(id = UUID.randomUUID(), sessionId = "sess-1").also {
        it.addItem(mouse, 2, VariantPricing(BigDecimal("69.99")), snapshot("Mouse"), 10)
        it.addItem(pad, 1, VariantPricing(BigDecimal("19.99")), snapshot("Pad"), 10)
    }

    private fun snapshot(name: String) =
        """{"productId":"${UUID.randomUUID()}","name":"$name","sku":"SKU","variantName":"Black","imageUrl":null}"""

    @BeforeEach
    fun setUp() {
        every { cartRepository.findBySessionIdAndStatusIn("sess-1", CartStatus.CURRENT) } returns cart
        every { cartRepository.save(any()) } answers { firstArg() }
        every { availabilityClient.issueWith(any()) } returns null.right()
        every { eventPublisher.publish(capture(published)) } returns Unit
    }

    private fun start() = useCase.execute(StartCheckoutCommand(owner, cart.id))

    @Test
    fun `locks an available cart and publishes CheckoutInitiated`() {
        val started = start().getOrNull()!!

        assertEquals(CartStatus.CHECKOUT, cart.status)
        assertEquals(cart.checkoutSessionId, started.session.id)
        verify { cartRepository.save(cart) }
        val payload = assertIs<CheckoutInitiated>(published.single()).payload
        assertEquals(cart.id, payload.cartId)
        assertEquals(started.session.id, payload.checkoutSessionId)
        assertEquals(started.session.expiresAt, payload.expiresAt)
        assertEquals(3, payload.itemCount)
        assertEquals(Money(BigDecimal("159.97"), "USD"), payload.subtotal)
        assertEquals("sess-1", payload.sessionId)
    }

    @Test
    fun `an empty cart is refused without checking stock`() {
        val empty = Cart(id = UUID.randomUUID(), sessionId = "sess-1")
        every { cartRepository.findBySessionIdAndStatusIn("sess-1", CartStatus.CURRENT) } returns empty

        assertEquals(CartError.CartEmpty(empty.id), useCase.execute(StartCheckoutCommand(owner, empty.id)).leftOrNull())
        verify(exactly = 0) { availabilityClient.issueWith(any()) }
    }

    @Test
    fun `lines that can't be ordered are listed and the cart stays unlocked`() {
        every { availabilityClient.issueWith(mouse) } returns AvailabilityIssue.OUT_OF_STOCK.right()
        every { availabilityClient.issueWith(pad) } returns AvailabilityIssue.NOT_AVAILABLE.right()
        val (mouseLine, padLine) = cart.items

        val error = assertIs<CartError.CartUnavailableItems>(start().leftOrNull())

        assertEquals(
            listOf(
                UnavailableLine(mouseLine.id, mouse, "Mouse", AvailabilityIssue.OUT_OF_STOCK),
                UnavailableLine(padLine.id, pad, "Pad", AvailabilityIssue.NOT_AVAILABLE)
            ),
            error.lines
        )
        assertEquals(CartStatus.ACTIVE, cart.status)
        verify(exactly = 0) { cartRepository.save(any()) }
        assertEquals(emptyList(), published)
    }

    @Test
    fun `a failed availability lookup refuses checkout rather than skipping the check`() {
        every { availabilityClient.issueWith(mouse) } returns CartError.AvailabilityUnavailable(mouse).left()

        assertEquals(CartError.AvailabilityUnavailable(mouse), start().leftOrNull())
        assertEquals(CartStatus.ACTIVE, cart.status)
    }

    @Test
    fun `starting again on a locked cart resumes the same session and publishes nothing new`() {
        val first = start().getOrNull()!!
        published.clear()

        val second = start().getOrNull()!!

        assertEquals(first.session.id, second.session.id)
        assertTrue(second.session.expiresAt >= first.session.expiresAt)
        assertEquals(emptyList(), published)
        verify(exactly = 2) { cartRepository.save(any()) }
        verify(exactly = 2) { availabilityClient.issueWith(any()) }
    }

    @Test
    fun `starting again after the session lapsed checks the cart again and starts a new session`() {
        val first = start().getOrNull()!!
        cart.checkoutExpiresAt = Instant.now().minusSeconds(1)
        published.clear()

        val second = start().getOrNull()!!

        assertNotEquals(first.session.id, second.session.id)
        assertIs<CheckoutInitiated>(published.single())
        verify(exactly = 4) { availabilityClient.issueWith(any()) }
    }

    @Test
    fun `a line added after the stock check is checked by the retry, not locked unchecked`() {
        val gone = UUID.randomUUID()
        fun cartAt(version: Long) = Cart(id = cart.id, sessionId = "sess-1", version = version).also {
            it.addItem(mouse, 2, VariantPricing(BigDecimal("69.99")), snapshot("Mouse"), 10)
        }
        val checked = cartAt(version = 0)
        val changed = cartAt(version = 1).also {
            it.addItem(gone, 1, VariantPricing(BigDecimal("9.99")), snapshot("Gone"), 10)
        }
        // The stock check reads the cart, then a concurrent add commits before the lock's re-read
        every { cartRepository.findBySessionIdAndStatusIn("sess-1", CartStatus.CURRENT) } returnsMany listOf(checked, changed)
        every { availabilityClient.issueWith(gone) } returns AvailabilityIssue.OUT_OF_STOCK.right()

        val error = assertIs<CartError.CartUnavailableItems>(start().leftOrNull())

        assertEquals(listOf(gone), error.lines.map { it.variantId })
        assertEquals(CartStatus.ACTIVE, changed.status)
        verify(exactly = 0) { cartRepository.save(any()) }
        assertEquals(emptyList(), published)
    }

    @Test
    fun `someone else's cart is not found`() {
        val otherId = UUID.randomUUID()

        assertEquals(CartError.CartNotFound(otherId), useCase.execute(StartCheckoutCommand(owner, otherId)).leftOrNull())
    }
}
