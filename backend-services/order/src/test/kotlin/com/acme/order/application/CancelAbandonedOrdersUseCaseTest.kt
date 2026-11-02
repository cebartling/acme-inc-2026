package com.acme.order.application

import com.acme.order.application.CancelAbandonedOrdersUseCase.Companion.PLACEMENT_ABANDONED
import com.acme.order.domain.Order
import com.acme.order.domain.OrderStatus
import com.acme.order.infrastructure.persistence.OrderRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.Pageable
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

class CancelAbandonedOrdersUseCaseTest {

    private val orders = mockk<OrderRepository>()
    private val transactionTemplate = TransactionTemplate(mockk<PlatformTransactionManager>(relaxed = true))
    private val meterRegistry = SimpleMeterRegistry()
    private val now = Instant.parse("2026-11-01T12:00:00Z")

    private fun useCase(maxAge: Duration = Duration.ofHours(1), batchSize: Int = 100) =
        CancelAbandonedOrdersUseCase(orders, transactionTemplate, meterRegistry, maxAge, batchSize)

    private fun pendingOrder() = Order(
        UUID.randomUUID(), "ACME-20261101-000001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "key-1",
        OrderStatus.PENDING, BigDecimal("159.98"), "USD", createdAt = now.minus(Duration.ofHours(2))
    )

    private fun cancelledCount() =
        meterRegistry.find("orders_cancelled_total").tag("reason", PLACEMENT_ABANDONED).counter()?.count() ?: 0.0

    @Test
    fun `cancels orders older than the max age with its own code and counts them`() {
        val abandoned = listOf(pendingOrder(), pendingOrder())
        every { orders.findAbandoned(now.minus(Duration.ofHours(1)), any()) } returns abandoned
        every { orders.markCancelled(any(), PLACEMENT_ABANDONED, now) } returns 1

        assertEquals(2, useCase().execute(now))

        abandoned.forEach { order -> verify { orders.markCancelled(order.id, PLACEMENT_ABANDONED, now) } }
        assertEquals(2.0, cancelledCount())
    }

    @Test
    fun `an order a retry submitted first is not counted`() {
        val submittedFirst = pendingOrder()
        val abandoned = pendingOrder()
        every { orders.findAbandoned(any(), any()) } returns listOf(submittedFirst, abandoned)
        every { orders.markCancelled(submittedFirst.id, any(), any()) } returns 0
        every { orders.markCancelled(abandoned.id, any(), any()) } returns 1

        assertEquals(1, useCase().execute(now))

        assertEquals(1.0, cancelledCount())
    }

    @Test
    fun `one run takes one batch`() {
        val page = slot<Pageable>()
        every { orders.findAbandoned(any(), capture(page)) } returns emptyList()

        assertEquals(0, useCase(batchSize = 7).execute(now))

        assertEquals(7, page.captured.pageSize)
        verify(exactly = 1) { orders.findAbandoned(any(), any()) }
    }

    @Test
    fun `refuses a max age a live checkout session could outlast`() {
        val error = assertThrows<IllegalArgumentException> { useCase(maxAge = Duration.ofMinutes(33)) }

        assertTrue(error.message!!.contains("acme.order.abandoned.max-age"))
        useCase(maxAge = Duration.ofMinutes(34))
    }

    @Test
    fun `refuses a batch size below one`() {
        assertThrows<IllegalArgumentException> { useCase(batchSize = 0) }
    }
}
