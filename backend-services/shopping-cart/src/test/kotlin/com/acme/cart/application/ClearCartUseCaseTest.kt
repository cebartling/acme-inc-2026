package com.acme.cart.application

import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartError
import com.acme.cart.domain.CartItem
import com.acme.cart.domain.CartOwner
import com.acme.cart.domain.CartStatus
import com.acme.cart.domain.VariantPricing
import com.acme.cart.domain.events.CartCleared
import com.acme.cart.domain.events.DomainEvent
import com.acme.cart.infrastructure.messaging.CartEventPublisher
import com.acme.cart.infrastructure.persistence.CartRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class ClearCartUseCaseTest {

    private val cartRepository = mockk<CartRepository>()
    private val eventPublisher = mockk<CartEventPublisher>()
    private val published = mutableListOf<DomainEvent>()

    private val useCase = ClearCartUseCase(
        cartRepository = cartRepository,
        eventPublisher = eventPublisher,
        transactionTemplate = TransactionTemplate(mockk<PlatformTransactionManager>(relaxed = true))
    )

    private val pricing = VariantPricing(BigDecimal("69.99"))
    private val cart = Cart(id = UUID.randomUUID(), sessionId = "sess-1").also {
        it.addItem(UUID.randomUUID(), 2, pricing, "{}", 10)
        it.addItem(UUID.randomUUID(), 3, pricing, "{}", 10)
    }

    @BeforeEach
    fun setUp() {
        every { cartRepository.findBySessionIdAndStatus("sess-1", CartStatus.ACTIVE) } returns cart
        every { cartRepository.save(any()) } answers { firstArg() }
        every { eventPublisher.publish(capture(published)) } returns Unit
    }

    @Test
    fun `clears every line and publishes CartCleared with what was removed`() {
        val updated = useCase.execute(ClearCartCommand(CartOwner.Guest("sess-1"), cart.id)).getOrNull()!!

        assertSame(cart, updated)
        assertEquals(emptyList(), updated.items)
        val payload = assertIs<CartCleared>(published.single()).payload
        assertEquals(cart.id, payload.cartId)
        assertEquals(2, payload.lineCount)
        assertEquals(5, payload.itemCount)
        assertEquals("sess-1", payload.sessionId)
        assertEquals(null, payload.userId)
    }

    @Test
    fun `a cart id the session does not own is CartNotFound and nothing changes`() {
        val otherCartId = UUID.randomUUID()

        val result = useCase.execute(ClearCartCommand(CartOwner.Guest("sess-1"), otherCartId))

        assertEquals(CartError.CartNotFound(otherCartId), result.leftOrNull())
        assertEquals(2, cart.items.size)
        verify(exactly = 0) { cartRepository.save(any()) }
        assertEquals(emptyList(), published)
    }

    @Test
    fun `clearing an empty cart returns it without saving or publishing`() {
        val empty = Cart(id = UUID.randomUUID(), sessionId = "sess-1")
        every { cartRepository.findBySessionIdAndStatus("sess-1", CartStatus.ACTIVE) } returns empty

        val result = useCase.execute(ClearCartCommand(CartOwner.Guest("sess-1"), empty.id))

        assertSame(empty, result.getOrNull())
        verify(exactly = 0) { cartRepository.save(any()) }
        assertEquals(emptyList(), published)
    }

    // --- PIN-278: a version conflict is retried once ------------------------------------------

    private fun conflict() = ObjectOptimisticLockingFailureException(Cart::class.java, cart.id)

    private fun freshCart() = Cart(id = cart.id, sessionId = "sess-1").also {
        it.items += CartItem(UUID.randomUUID(), it, UUID.randomUUID(), 2, BigDecimal("69.99"), "{}")
    }

    @Test
    fun `a version conflict is retried once against a fresh read, and published once`() {
        every { cartRepository.findBySessionIdAndStatus("sess-1", CartStatus.ACTIVE) } answers { freshCart() }
        every { cartRepository.save(any()) } throws conflict() andThenAnswer { firstArg() }

        val updated = useCase.execute(ClearCartCommand(CartOwner.Guest("sess-1"), cart.id)).getOrNull()!!

        assertEquals(emptyList(), updated.items)
        assertEquals(1, published.size)
    }

    @Test
    fun `a second conflict in a row is left to the caller`() {
        every { cartRepository.findBySessionIdAndStatus("sess-1", CartStatus.ACTIVE) } answers { freshCart() }
        every { cartRepository.save(any()) } throws conflict()

        assertFailsWith<ObjectOptimisticLockingFailureException> {
            useCase.execute(ClearCartCommand(CartOwner.Guest("sess-1"), cart.id))
        }
        assertEquals(emptyList(), published)
    }
}
