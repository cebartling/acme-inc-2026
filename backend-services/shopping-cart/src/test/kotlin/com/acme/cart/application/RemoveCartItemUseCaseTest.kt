package com.acme.cart.application

import com.acme.cart.domain.CartOwner
import com.acme.cart.domain.CartStatus
import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartError
import com.acme.cart.domain.VariantPricing
import com.acme.cart.domain.events.CartItemRemoved
import com.acme.cart.domain.events.DomainEvent
import com.acme.cart.infrastructure.messaging.CartEventPublisher
import com.acme.cart.infrastructure.persistence.CartRepository
import org.springframework.orm.ObjectOptimisticLockingFailureException
import kotlin.test.assertFailsWith
import com.acme.cart.domain.CartItem
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

class RemoveCartItemUseCaseTest {

    private val cartRepository = mockk<CartRepository>()
    private val eventPublisher = mockk<CartEventPublisher>()
    private val published = mutableListOf<DomainEvent>()

    private val useCase = RemoveCartItemUseCase(
        cartRepository = cartRepository,
        eventPublisher = eventPublisher,
        transactionTemplate = TransactionTemplate(mockk<PlatformTransactionManager>(relaxed = true))
    )

    private val cart = Cart(id = UUID.randomUUID(), sessionId = "sess-1")
    private val item = cart.addItem(UUID.randomUUID(), 2, VariantPricing(BigDecimal("69.99")), "{}", 10).getOrNull()!!

    @BeforeEach
    fun setUp() {
        every { cartRepository.findBySessionIdAndStatus("sess-1", CartStatus.ACTIVE) } returns cart
        every { cartRepository.save(any()) } answers { firstArg() }
        every { eventPublisher.publish(capture(published)) } returns Unit
    }

    @Test
    fun `removes the line and publishes CartItemRemoved with the removed quantity`() {
        val updated = useCase.execute(RemoveCartItemCommand(CartOwner.Guest("sess-1"), cart.id, item.id)).getOrNull()!!

        assertEquals(emptyList(), updated.items)
        val payload = assertIs<CartItemRemoved>(published.single()).payload
        assertEquals(item.id, payload.cartItemId)
        assertEquals(2, payload.quantity)
    }

    @Test
    fun `a cart id the session does not own is CartItemNotFound and nothing changes`() {
        val result = useCase.execute(RemoveCartItemCommand(CartOwner.Guest("sess-1"), UUID.randomUUID(), item.id))

        assertEquals(CartError.CartItemNotFound(item.id), result.leftOrNull())
        assertEquals(1, cart.items.size)
        verify(exactly = 0) { cartRepository.save(any()) }
        assertEquals(emptyList(), published)
    }

    // --- PIN-278: a version conflict is retried once ------------------------------------------

    private fun conflict() = ObjectOptimisticLockingFailureException(Cart::class.java, cart.id)

    private fun freshCart() = Cart(id = cart.id, sessionId = "sess-1").also {
        it.items += CartItem(item.id, it, item.variantId, 2, BigDecimal("69.99"), "{}")
    }

    @Test
    fun `a version conflict is retried once against a fresh read, and published once`() {
        every { cartRepository.findBySessionIdAndStatus("sess-1", CartStatus.ACTIVE) } answers { freshCart() }
        every { cartRepository.save(any()) } throws conflict() andThenAnswer { firstArg() }

        val updated = useCase.execute(RemoveCartItemCommand(CartOwner.Guest("sess-1"), cart.id, item.id)).getOrNull()!!

        assertEquals(emptyList(), updated.items)
        assertEquals(1, published.size)
    }

    @Test
    fun `a retry that finds the line already gone is a CartItemNotFound, not a failure`() {
        // The first attempt read the line before a concurrent request removed it and committed;
        // the retry reads the committed cart, which no longer has it.
        every { cartRepository.findBySessionIdAndStatus("sess-1", CartStatus.ACTIVE) } returnsMany
            listOf(freshCart(), Cart(id = cart.id, sessionId = "sess-1"))
        every { cartRepository.save(any()) } throws conflict() andThenAnswer { firstArg() }

        val result = useCase.execute(RemoveCartItemCommand(CartOwner.Guest("sess-1"), cart.id, item.id))

        assertEquals(CartError.CartItemNotFound(item.id), result.leftOrNull())
        assertEquals(emptyList(), published)
    }

    @Test
    fun `a second conflict in a row is left to the caller`() {
        every { cartRepository.findBySessionIdAndStatus("sess-1", CartStatus.ACTIVE) } answers { freshCart() }
        every { cartRepository.save(any()) } throws conflict()

        assertFailsWith<ObjectOptimisticLockingFailureException> {
            useCase.execute(RemoveCartItemCommand(CartOwner.Guest("sess-1"), cart.id, item.id))
        }
        assertEquals(emptyList(), published)
    }
}
