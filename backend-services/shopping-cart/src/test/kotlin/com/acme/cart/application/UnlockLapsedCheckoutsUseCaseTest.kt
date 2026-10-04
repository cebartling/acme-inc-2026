package com.acme.cart.application

import com.acme.cart.domain.events.CheckoutSessionExpired
import com.acme.cart.domain.events.DomainEvent
import com.acme.cart.infrastructure.messaging.CartEventPublisher
import com.acme.cart.infrastructure.persistence.CartRepository
import com.acme.cart.infrastructure.persistence.LapsedCheckout
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UnlockLapsedCheckoutsUseCaseTest {

    private val cartRepository = mockk<CartRepository>()
    private val eventPublisher = mockk<CartEventPublisher>()
    private val meterRegistry = SimpleMeterRegistry()
    private val published = mutableListOf<DomainEvent>()

    private val useCase = UnlockLapsedCheckoutsUseCase(cartRepository, CheckoutSessionExpiry(eventPublisher, meterRegistry))

    private val now = Instant.parse("2026-10-03T12:00:00Z")

    private fun lapsed(lineCount: Int = 2, itemCount: Long = 3) = LapsedCheckout(
        id = UUID.randomUUID(),
        checkoutSessionId = UUID.randomUUID(),
        expiredAt = now.minusSeconds(90),
        sessionId = "sess-${UUID.randomUUID()}",
        userId = null,
        lineCount = lineCount,
        itemCount = itemCount
    )

    private fun expiredCount() = meterRegistry.counter(CheckoutSessionExpiry.EXPIRED_METRIC).count()

    @BeforeEach
    fun setUp() {
        every { eventPublisher.publish(capture(published)) } returns Unit
        every { cartRepository.unlockIfLapsed(any(), now) } returns 1
    }

    @Test
    fun `nothing lapsed unlocks nothing`() {
        every { cartRepository.findLapsedCheckouts(now, any()) } returns emptyList()

        assertEquals(0, useCase.execute(now))
        assertEquals(emptyList(), published)
        assertEquals(0.0, expiredCount())
    }

    @Test
    fun `each lapsed cart is unlocked and announced with a CheckoutSessionExpired event`() {
        val cart = lapsed(lineCount = 3, itemCount = 5)
        every { cartRepository.findLapsedCheckouts(now, any()) } returnsMany listOf(listOf(cart), emptyList())

        assertEquals(1, useCase.execute(now))

        val payload = assertIs<CheckoutSessionExpired>(published.single()).payload
        assertEquals(cart.id, payload.cartId)
        assertEquals(cart.checkoutSessionId, payload.checkoutSessionId)
        assertEquals(cart.expiredAt, payload.expiredAt)
        assertEquals(3, payload.lineCount)
        assertEquals(5, payload.itemCount)
        assertEquals(cart.sessionId, payload.sessionId)
        assertEquals(1.0, expiredCount())
    }

    @Test
    fun `a session resumed after the scan is not announced`() {
        val stillLapsed = lapsed()
        val resumed = lapsed()
        every { cartRepository.findLapsedCheckouts(now, any()) } returnsMany listOf(listOf(stillLapsed, resumed), emptyList())
        every { cartRepository.unlockIfLapsed(resumed.id, now) } returns 0

        assertEquals(1, useCase.execute(now))
        assertEquals(listOf(stillLapsed.id), published.map { (it as CheckoutSessionExpired).payload.cartId })
    }

    @Test
    fun `batches are drained until none are left`() {
        val first = List(UnlockLapsedCheckoutsUseCase.BATCH_SIZE) { lapsed() }
        val second = listOf(lapsed())
        every { cartRepository.findLapsedCheckouts(now, any()) } returnsMany listOf(first, second)

        assertEquals(UnlockLapsedCheckoutsUseCase.BATCH_SIZE + 1, useCase.execute(now))
        verify(exactly = 2) { cartRepository.findLapsedCheckouts(now, any<Pageable>()) }
    }

    @Test
    fun `a batch that unlocks nothing stops the run instead of looping`() {
        val stuck = List(UnlockLapsedCheckoutsUseCase.BATCH_SIZE) { lapsed() }
        every { cartRepository.findLapsedCheckouts(now, any()) } returns stuck
        every { cartRepository.unlockIfLapsed(any(), now) } returns 0

        assertEquals(0, useCase.execute(now))
        verify(exactly = 1) { cartRepository.findLapsedCheckouts(now, any<Pageable>()) }
    }

    @Test
    fun `a Kafka failure does not stop the unlock`() {
        every { cartRepository.findLapsedCheckouts(now, any()) } returnsMany listOf(listOf(lapsed(), lapsed()), emptyList())
        every { eventPublisher.publish(any()) } throws IllegalStateException("broker down")

        assertEquals(2, useCase.execute(now))
        assertEquals(2.0, expiredCount())
    }
}
