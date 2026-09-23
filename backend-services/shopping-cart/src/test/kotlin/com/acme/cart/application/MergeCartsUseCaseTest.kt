package com.acme.cart.application

import arrow.core.left
import arrow.core.right
import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartError
import com.acme.cart.domain.CartOwner
import com.acme.cart.domain.CartStatus
import com.acme.cart.domain.VariantPricing
import com.acme.cart.domain.events.CartMerged
import com.acme.cart.domain.events.DomainEvent
import com.acme.cart.domain.newCartFor
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MergeCartsUseCaseTest {

    private val cartRepository = mockk<CartRepository>()
    private val pricingClient = mockk<ProductPricingClient>()
    private val eventPublisher = mockk<CartEventPublisher>()
    private val published = mutableListOf<DomainEvent>()

    private val useCase = MergeCartsUseCase(
        cartRepository = cartRepository,
        pricingClient = pricingClient,
        eventPublisher = eventPublisher,
        transactionTemplate = TransactionTemplate(mockk<PlatformTransactionManager>(relaxed = true)),
        maxOrderQuantity = 10
    )

    private val userId = UUID.randomUUID()
    private val variantId = UUID.randomUUID()
    private val pricing = VariantPricing(BigDecimal("69.99"))

    private fun guestWith(quantity: Int): Cart =
        newCartFor(CartOwner.Guest("sess-1")).apply { addItem(variantId, quantity, pricing, "{}", 10) }

    private fun givenCarts(guest: Cart?, user: Cart?) {
        every { cartRepository.findBySessionIdAndStatus("sess-1", CartStatus.ACTIVE) } returns guest
        every { cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE) } returns user
    }

    @BeforeEach
    fun setUp() {
        every { cartRepository.save(any()) } answers { firstArg() }
        every { pricingClient.getPricing(variantId) } returns pricing.right()
        every { eventPublisher.publish(capture(published)) } returns Unit
    }

    @Test
    fun `merges the guest cart into a new user cart and publishes CartMerged (AC-01, AC-10)`() {
        val guest = guestWith(2)
        givenCarts(guest = guest, user = null)

        val outcome = useCase.execute(MergeCartsCommand(userId, "sess-1")).getOrNull()!!

        assertEquals(userId, outcome.cart?.userId)
        assertEquals(2, outcome.cart?.itemCount)
        assertEquals(1, outcome.result?.itemsMerged)
        assertEquals(CartStatus.MERGED, guest.status)
        verify { cartRepository.save(guest) }

        val payload = assertIs<CartMerged>(published.single()).payload
        assertEquals(outcome.cart!!.id, payload.targetCartId)
        assertEquals(guest.id, payload.sourceCartId)
        assertEquals(userId, payload.userId)
        assertEquals(1, payload.itemsMerged)
        assertEquals(0, payload.quantitiesAdjusted)
    }

    @Test
    fun `no session is a no-op that returns the user's cart unchanged (AC-09)`() {
        val user = newCartFor(CartOwner.Customer(userId))
        every { cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE) } returns user

        val outcome = useCase.execute(MergeCartsCommand(userId, guestSessionId = null)).getOrNull()!!

        assertEquals(user, outcome.cart)
        assertNull(outcome.result)
        verify(exactly = 0) { cartRepository.save(any()) }
        assertEquals(emptyList(), published)
    }

    @Test
    fun `an empty or already MERGED guest cart is a no-op with no event (AC-08, AC-05)`() {
        givenCarts(guest = newCartFor(CartOwner.Guest("sess-1")), user = null)

        val emptyGuest = useCase.execute(MergeCartsCommand(userId, "sess-1")).getOrNull()!!

        // A MERGED cart is never returned by the ACTIVE lookup, so it reads as no guest cart.
        givenCarts(guest = null, user = null)
        val alreadyMerged = useCase.execute(MergeCartsCommand(userId, "sess-1")).getOrNull()!!

        assertNull(emptyGuest.cart)
        assertNull(emptyGuest.result)
        assertNull(alreadyMerged.result)
        verify(exactly = 0) { pricingClient.getPricing(any()) }
        verify(exactly = 0) { cartRepository.save(any()) }
        assertEquals(emptyList(), published)
    }

    @Test
    fun `a capped merge reports the adjustment in the result and the event (AC-04)`() {
        val user = newCartFor(CartOwner.Customer(userId)).apply { addItem(variantId, 8, pricing, "{}", 10) }
        givenCarts(guest = guestWith(5), user = user)

        val outcome = useCase.execute(MergeCartsCommand(userId, "sess-1")).getOrNull()!!

        assertEquals(10, user.items.single().quantity)
        assertEquals(13, outcome.result!!.quantitiesAdjusted.single().requestedTotal)
        assertEquals(1, assertIs<CartMerged>(published.single()).payload.quantitiesAdjusted)
    }

    @Test
    fun `a pricing failure merges nothing`() {
        val guest = guestWith(2)
        givenCarts(guest = guest, user = null)
        every { pricingClient.getPricing(variantId) } returns CartError.PricingUnavailable(variantId).left()

        val result = useCase.execute(MergeCartsCommand(userId, "sess-1"))

        assertEquals(CartError.PricingUnavailable(variantId), result.leftOrNull())
        assertEquals(CartStatus.ACTIVE, guest.status)
        verify(exactly = 0) { cartRepository.save(any()) }
        assertEquals(emptyList(), published)
    }

    @Test
    fun `a Kafka failure does not fail the merge`() {
        givenCarts(guest = guestWith(1), user = null)
        every { eventPublisher.publish(any()) } throws IllegalStateException("broker down")

        assertNotNull(useCase.execute(MergeCartsCommand(userId, "sess-1")).getOrNull()?.result)
    }
}
