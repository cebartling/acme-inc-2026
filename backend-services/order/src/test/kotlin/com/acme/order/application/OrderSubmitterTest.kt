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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(OutputCaptureExtension::class)
class OrderSubmitterTest {

    private val orders = mockk<OrderRepository>()
    private val outboxWriter = mockk<OutboxWriter>()
    private val transactionTemplate = TransactionTemplate(mockk<PlatformTransactionManager>(relaxed = true))
    private val submitter = OrderSubmitter(orders, outboxWriter, transactionTemplate)

    private fun order(status: OrderStatus, id: UUID = UUID.randomUUID()) = Order(
        id, "ACME-20261101-000001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "key-1",
        status, BigDecimal("159.98"), "USD", createdAt = Instant.parse("2026-11-01T10:00:00Z")
    )

    /** The order a caller holds is PENDING; the update touches [rows] of it, and the re-read finds [current]. */
    private fun markSubmittedReturns(rows: Int, current: Order): Order {
        val held = order(OrderStatus.PENDING, current.id)
        every { orders.markSubmitted(current.id, any()) } returns rows
        every { orders.findById(current.id) } returns Optional.of(current)
        return held
    }

    @Test
    fun `submits a PENDING order and writes OrderCreated once`() {
        val held = markSubmittedReturns(1, order(OrderStatus.SUBMITTED))
        every { outboxWriter.write(any()) } just runs

        val result = submitter.submit(held)

        val submitted = (result as SubmitResult.Submitted).order
        assertEquals(OrderStatus.SUBMITTED, submitted.status)
        assertEquals(held.id, submitted.id)
        verify(exactly = 1) { outboxWriter.write(match { it is OrderCreated }) }
    }

    @Test
    fun `an order another caller already submitted is reported, and OrderCreated is not written again`(output: CapturedOutput) {
        val reread = order(OrderStatus.SUBMITTED)
        val held = markSubmittedReturns(0, reread)

        val result = submitter.submit(held)

        assertEquals(SubmitResult.AlreadySubmitted(reread), result)
        assertSame(reread, result.order)
        verify(exactly = 0) { outboxWriter.write(any()) }
        assertFalse("has no cart and no order" in output.out, output.out)
    }

    @Test
    fun `an order a racing caller cancelled is reported and logged, and OrderCreated is not written`(output: CapturedOutput) {
        val reread = order(OrderStatus.CANCELLED)
        val held = markSubmittedReturns(0, reread)

        val result = submitter.submit(held)

        assertEquals(SubmitResult.Cancelled(reread), result)
        assertSame(reread, result.order)
        verify(exactly = 0) { outboxWriter.write(any()) }
        assertTrue("${reread.orderNumber}), which was cancelled with null: user ${reread.userId} has no cart and no order" in output.out, output.out)
    }

    @Test
    fun `an order still PENDING after the update changed no row fails instead of being reported as submitted`() {
        val held = markSubmittedReturns(0, order(OrderStatus.PENDING))

        assertThrows<IllegalStateException> { submitter.submit(held) }

        verify(exactly = 0) { outboxWriter.write(any()) }
    }
}
