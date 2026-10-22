package com.acme.cart.application

import com.acme.cart.domain.events.CheckoutSessionExpired
import com.acme.cart.domain.events.DomainEvent
import com.acme.cart.infrastructure.messaging.OutboxWriter
import com.acme.cart.infrastructure.persistence.CartRepository
import com.acme.cart.infrastructure.persistence.LapsedCheckout
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class UnlockLapsedCheckoutsUseCaseTest {

    private val cartRepository = mockk<CartRepository>()
    private val outboxWriter = mockk<OutboxWriter>()
    private val transactionManager = mockk<PlatformTransactionManager>(relaxed = true)
    private val meterRegistry = SimpleMeterRegistry()
    private val published = mutableListOf<DomainEvent>()

    private val grace = Duration.ofMinutes(2)
    private val useCase = UnlockLapsedCheckoutsUseCase(cartRepository, CheckoutSessionExpiry(outboxWriter, meterRegistry), TransactionTemplate(transactionManager), grace)

    private val now = Instant.parse("2026-10-03T12:00:00Z")
    /** Sessions are only unlocked once lapsed for the grace period, so this is what is asked for. */
    private val cutoff = now.minus(grace)

    private fun lapsed(lineCount: Int = 2, itemCount: Long = 3) = LapsedCheckout(
        id = UUID.randomUUID(),
        checkoutSessionId = UUID.randomUUID(),
        expiredAt = cutoff.minusSeconds(90),
        sessionId = "sess-${UUID.randomUUID()}",
        userId = null,
        lineCount = lineCount,
        itemCount = itemCount
    )

    private fun expiredCount() = meterRegistry.counter(CheckoutSessionExpiry.EXPIRED_METRIC).count()

    @BeforeEach
    fun setUp() {
        every { outboxWriter.write(capture(published)) } returns Unit
        every { cartRepository.unlockIfLapsed(any(), cutoff, now) } returns 1
    }

    @Test
    fun `nothing lapsed unlocks nothing`() {
        every { cartRepository.findLapsedCheckouts(cutoff, any()) } returns emptyList()

        assertEquals(0, useCase.execute(now))
        assertEquals(emptyList(), published)
        assertEquals(0.0, expiredCount())
    }

    @Test
    fun `each lapsed cart is unlocked and announced with a CheckoutSessionExpired event`() {
        val cart = lapsed(lineCount = 3, itemCount = 5)
        every { cartRepository.findLapsedCheckouts(cutoff, any()) } returnsMany listOf(listOf(cart), emptyList())

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
        every { cartRepository.findLapsedCheckouts(cutoff, any()) } returnsMany listOf(listOf(stillLapsed, resumed), emptyList())
        every { cartRepository.unlockIfLapsed(resumed.id, cutoff, now) } returns 0

        assertEquals(1, useCase.execute(now))
        assertEquals(listOf(stillLapsed.id), published.map { (it as CheckoutSessionExpired).payload.cartId })
    }

    @Test
    fun `batches are drained until none are left`() {
        val first = List(UnlockLapsedCheckoutsUseCase.BATCH_SIZE) { lapsed() }
        val second = listOf(lapsed())
        every { cartRepository.findLapsedCheckouts(cutoff, any()) } returnsMany listOf(first, second)

        assertEquals(UnlockLapsedCheckoutsUseCase.BATCH_SIZE + 1, useCase.execute(now))
        verify(exactly = 2) { cartRepository.findLapsedCheckouts(cutoff, any<Pageable>()) }
    }

    @Test
    fun `a batch that unlocks nothing stops the run instead of looping`() {
        val stuck = List(UnlockLapsedCheckoutsUseCase.BATCH_SIZE) { lapsed() }
        every { cartRepository.findLapsedCheckouts(cutoff, any()) } returns stuck
        every { cartRepository.unlockIfLapsed(any(), cutoff, now) } returns 0

        assertEquals(0, useCase.execute(now))
        verify(exactly = 1) { cartRepository.findLapsedCheckouts(cutoff, any<Pageable>()) }
    }

    @Test
    fun `a negative grace is rejected at startup, but no grace is allowed`() {
        assertFailsWith<IllegalArgumentException> {
            UnlockLapsedCheckoutsUseCase(cartRepository, CheckoutSessionExpiry(outboxWriter, meterRegistry), TransactionTemplate(transactionManager), Duration.ofMinutes(-2))
        }
        UnlockLapsedCheckoutsUseCase(cartRepository, CheckoutSessionExpiry(outboxWriter, meterRegistry), TransactionTemplate(transactionManager), Duration.ZERO)
    }

    @Test
    fun `writes each cart's event to the outbox before its transaction commits`() {
        every { cartRepository.findLapsedCheckouts(cutoff, any()) } returnsMany listOf(listOf(lapsed()), emptyList())

        assertEquals(1, useCase.execute(now))
        verifyOrder {
            cartRepository.unlockIfLapsed(any(), cutoff, now)
            outboxWriter.write(any())
            transactionManager.commit(any())
        }
    }
}
