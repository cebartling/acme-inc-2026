package com.acme.order.application

import com.acme.order.domain.Order
import com.acme.order.domain.OrderStatus
import com.acme.order.domain.events.OrderCreated
import com.acme.order.infrastructure.messaging.OutboxWriter
import com.acme.order.infrastructure.persistence.OrderRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

class OrderSubmitterTest {

    private val orders = mockk<OrderRepository>()
    private val outboxWriter = mockk<OutboxWriter>()
    private val transactionTemplate = TransactionTemplate(mockk<PlatformTransactionManager>(relaxed = true))
    private val submitter = OrderSubmitter(orders, outboxWriter, transactionTemplate)

    private fun order(status: OrderStatus) = Order(
        UUID.randomUUID(), "ACME-20261101-000001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "key-1",
        status, BigDecimal("159.98"), "USD", createdAt = Instant.parse("2026-11-01T10:00:00Z")
    )

    private fun markSubmittedReturns(rows: Int, current: Order) {
        every { orders.markSubmitted(current.id, any()) } returns rows
        every { orders.findById(current.id) } returns Optional.of(current)
    }

    @Test
    fun `submits a PENDING order and writes OrderCreated once`() {
        val submitted = order(OrderStatus.SUBMITTED)
        markSubmittedReturns(1, submitted)
        every { outboxWriter.write(any()) } just runs

        val result = submitter.submit(submitted)

        assertEquals(SubmitResult.Submitted(submitted), result)
        verify(exactly = 1) { outboxWriter.write(match { it is OrderCreated }) }
    }

    @Test
    fun `an order another caller already submitted is reported, and OrderCreated is not written again`() {
        val submitted = order(OrderStatus.SUBMITTED)
        markSubmittedReturns(0, submitted)

        val result = submitter.submit(submitted)

        assertEquals(SubmitResult.AlreadySubmitted(submitted), result)
        verify(exactly = 0) { outboxWriter.write(any()) }
    }

    @Test
    fun `an order a racing caller cancelled is reported, and OrderCreated is not written`() {
        val cancelled = order(OrderStatus.CANCELLED)
        markSubmittedReturns(0, cancelled)

        val result = submitter.submit(cancelled)

        assertEquals(SubmitResult.Cancelled(cancelled), result)
        verify(exactly = 0) { outboxWriter.write(any()) }
    }
}
