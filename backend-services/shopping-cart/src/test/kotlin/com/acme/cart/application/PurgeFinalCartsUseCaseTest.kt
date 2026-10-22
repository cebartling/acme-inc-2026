package com.acme.cart.application

import com.acme.cart.domain.CartStatus
import com.acme.cart.domain.events.CartPurged
import com.acme.cart.domain.events.DomainEvent
import com.acme.cart.infrastructure.messaging.OutboxWriter
import com.acme.cart.infrastructure.persistence.CartRepository
import com.acme.cart.infrastructure.persistence.FinalCart
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class PurgeFinalCartsUseCaseTest {

    private val cartRepository = mockk<CartRepository>()
    private val outboxWriter = mockk<OutboxWriter>()
    private val transactionManager = mockk<PlatformTransactionManager>(relaxed = true)
    private val meterRegistry = SimpleMeterRegistry()
    private val published = mutableListOf<DomainEvent>()

    private val useCase = PurgeFinalCartsUseCase(
        cartRepository = cartRepository,
        outboxWriter = outboxWriter,
        transactionTemplate = TransactionTemplate(transactionManager),
        retention = Duration.ofDays(90),
        meterRegistry = meterRegistry
    )

    private val now = Instant.parse("2026-09-25T12:00:00Z")
    private val cutoff = now.minus(Duration.ofDays(90))

    private fun final(status: CartStatus = CartStatus.EXPIRED) =
        FinalCart(UUID.randomUUID(), status, now.minus(Duration.ofDays(100)))

    private fun purgedCount() = meterRegistry.counter(PurgeFinalCartsUseCase.PURGED_METRIC).count()

    @BeforeEach
    fun setUp() {
        every { outboxWriter.write(capture(published)) } returns Unit
        every { cartRepository.deleteIfFinalBefore(any(), cutoff) } returns 1
    }

    @Test
    fun `nothing past retention deletes nothing`() {
        every { cartRepository.findFinalCartsBefore(cutoff, any()) } returns emptyList()

        assertEquals(0, useCase.execute(now))
        assertEquals(emptyList(), published)
        assertEquals(0.0, purgedCount())
    }

    @Test
    fun `each deleted cart is announced with a CartPurged event`() {
        val expired = final(CartStatus.EXPIRED)
        val merged = final(CartStatus.MERGED)
        every { cartRepository.findFinalCartsBefore(cutoff, any()) } returnsMany listOf(listOf(expired, merged), emptyList())

        assertEquals(2, useCase.execute(now))

        val payloads = published.map { assertIs<CartPurged>(it).payload }
        assertEquals(listOf(expired.id, merged.id), payloads.map { it.cartId })
        assertEquals(listOf(CartStatus.EXPIRED, CartStatus.MERGED), payloads.map { it.finalStatus })
        assertEquals(expired.finalizedAt, payloads[0].finalizedAt)
        assertEquals(2.0, purgedCount())
    }

    @Test
    fun `the event never carries the guest session ID`() {
        every { cartRepository.findFinalCartsBefore(cutoff, any()) } returnsMany listOf(listOf(final(CartStatus.MERGED)), emptyList())

        useCase.execute(now)

        // A merged cart's session can still be the live key to a newer guest cart.
        val json = jacksonObjectMapper().findAndRegisterModules().writeValueAsString(assertIs<CartPurged>(published.single()).payload)
        assertFalse("sessionId" in json, json)
    }

    @Test
    fun `a cart the delete skipped is not announced`() {
        val gone = final()
        val skipped = final()
        every { cartRepository.findFinalCartsBefore(cutoff, any()) } returnsMany listOf(listOf(gone, skipped), emptyList())
        every { cartRepository.deleteIfFinalBefore(skipped.id, cutoff) } returns 0

        assertEquals(1, useCase.execute(now))
        assertEquals(listOf(gone.id), published.map { (it as CartPurged).payload.cartId })
    }

    @Test
    fun `batches are drained until none are left`() {
        val first = List(PurgeFinalCartsUseCase.BATCH_SIZE) { final() }
        val second = listOf(final())
        every { cartRepository.findFinalCartsBefore(cutoff, any()) } returnsMany listOf(first, second)

        assertEquals(PurgeFinalCartsUseCase.BATCH_SIZE + 1, useCase.execute(now))
        verify(exactly = 2) { cartRepository.findFinalCartsBefore(cutoff, any<Pageable>()) }
    }

    @Test
    fun `a batch that deletes nothing stops the run instead of looping`() {
        val stuck = List(PurgeFinalCartsUseCase.BATCH_SIZE) { final() }
        every { cartRepository.findFinalCartsBefore(cutoff, any()) } returns stuck
        every { cartRepository.deleteIfFinalBefore(any(), cutoff) } returns 0

        assertEquals(0, useCase.execute(now))
        verify(exactly = 1) { cartRepository.findFinalCartsBefore(cutoff, any<Pageable>()) }
    }

    @Test
    fun `writes each cart's event to the outbox before its transaction commits`() {
        every { cartRepository.findFinalCartsBefore(cutoff, any()) } returnsMany listOf(listOf(final()), emptyList())

        assertEquals(1, useCase.execute(now))
        verifyOrder {
            cartRepository.deleteIfFinalBefore(any(), cutoff)
            outboxWriter.write(any())
            transactionManager.commit(any())
        }
    }

    @Test
    fun `a retention that is not positive is rejected at startup`() {
        listOf(Duration.ZERO, Duration.ofDays(-90)).forEach { retention ->
            assertFailsWith<IllegalArgumentException> {
                PurgeFinalCartsUseCase(cartRepository, outboxWriter, TransactionTemplate(transactionManager), retention, meterRegistry)
            }
        }
    }
}
