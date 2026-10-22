package com.acme.cart.application

import com.acme.cart.domain.events.CartExpired
import com.acme.cart.domain.events.DomainEvent
import com.acme.cart.infrastructure.messaging.OutboxWriter
import com.acme.cart.infrastructure.persistence.CartRepository
import com.acme.cart.infrastructure.persistence.IdleGuestCart
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

class ExpireIdleGuestCartsUseCaseTest {

    private val cartRepository = mockk<CartRepository>()
    private val outboxWriter = mockk<OutboxWriter>()
    private val transactionManager = mockk<PlatformTransactionManager>(relaxed = true)
    private val meterRegistry = SimpleMeterRegistry()
    private val published = mutableListOf<DomainEvent>()

    private val useCase = ExpireIdleGuestCartsUseCase(
        cartRepository = cartRepository,
        outboxWriter = outboxWriter,
        transactionTemplate = TransactionTemplate(transactionManager),
        guestTtl = Duration.ofDays(30),
        meterRegistry = meterRegistry
    )

    private val now = Instant.parse("2026-09-24T12:00:00Z")
    /** Guest TTL plus a day of grace for the once-a-day activity refresh. */
    private val cutoff = now.minus(Duration.ofDays(31))

    private fun idle(lineCount: Int = 2) =
        IdleGuestCart(UUID.randomUUID(), "sess-${UUID.randomUUID()}", now.minus(Duration.ofDays(40)), lineCount)

    private fun expiredCount() = meterRegistry.counter(ExpireIdleGuestCartsUseCase.EXPIRED_METRIC).count()

    @BeforeEach
    fun setUp() {
        every { outboxWriter.write(capture(published)) } returns Unit
        every { cartRepository.expireIfIdle(any(), cutoff, now) } returns 1
    }

    @Test
    fun `nothing idle expires nothing`() {
        every { cartRepository.findIdleGuestCarts(cutoff, any()) } returns emptyList()

        assertEquals(0, useCase.execute(now))
        assertEquals(emptyList(), published)
        assertEquals(0.0, expiredCount())
    }

    @Test
    fun `each idle cart is expired and announced with a CartExpired event`() {
        val cart = idle(lineCount = 3)
        every { cartRepository.findIdleGuestCarts(cutoff, any()) } returnsMany listOf(listOf(cart), emptyList())

        assertEquals(1, useCase.execute(now))

        val payload = assertIs<CartExpired>(published.single()).payload
        assertEquals(cart.id, payload.cartId)
        assertEquals(cart.sessionId, payload.sessionId)
        assertEquals(cart.lastActiveAt, payload.lastActiveAt)
        assertEquals(3, payload.lineCount)
        assertEquals(1.0, expiredCount())
    }

    @Test
    fun `a cart that became active again after the scan is not announced`() {
        val stillIdle = idle()
        val revived = idle()
        every { cartRepository.findIdleGuestCarts(cutoff, any()) } returnsMany listOf(listOf(stillIdle, revived), emptyList())
        every { cartRepository.expireIfIdle(revived.id, cutoff, now) } returns 0

        assertEquals(1, useCase.execute(now))
        assertEquals(listOf(stillIdle.id), published.map { (it as CartExpired).payload.cartId })
    }

    @Test
    fun `batches are drained until none are left`() {
        val first = List(ExpireIdleGuestCartsUseCase.BATCH_SIZE) { idle() }
        val second = listOf(idle())
        every { cartRepository.findIdleGuestCarts(cutoff, any()) } returnsMany listOf(first, second)

        assertEquals(ExpireIdleGuestCartsUseCase.BATCH_SIZE + 1, useCase.execute(now))
        verify(exactly = 2) { cartRepository.findIdleGuestCarts(cutoff, any<Pageable>()) }
    }

    @Test
    fun `a batch that expires nothing stops the run instead of looping`() {
        val stuck = List(ExpireIdleGuestCartsUseCase.BATCH_SIZE) { idle() }
        every { cartRepository.findIdleGuestCarts(cutoff, any()) } returns stuck
        every { cartRepository.expireIfIdle(any(), cutoff, now) } returns 0

        assertEquals(0, useCase.execute(now))
        verify(exactly = 1) { cartRepository.findIdleGuestCarts(cutoff, any<Pageable>()) }
    }

    @Test
    fun `a guest TTL that is not positive is rejected at startup`() {
        listOf(Duration.ZERO, Duration.ofDays(-30)).forEach { ttl ->
            assertFailsWith<IllegalArgumentException> {
                ExpireIdleGuestCartsUseCase(cartRepository, outboxWriter, TransactionTemplate(transactionManager), ttl, meterRegistry)
            }
        }
    }

    @Test
    fun `writes each cart's event to the outbox before its transaction commits`() {
        every { cartRepository.findIdleGuestCarts(cutoff, any()) } returnsMany listOf(listOf(idle()), emptyList())

        assertEquals(1, useCase.execute(now))
        verifyOrder {
            cartRepository.expireIfIdle(any(), cutoff, now)
            outboxWriter.write(any())
            transactionManager.commit(any())
        }
    }
}
