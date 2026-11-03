package com.acme.order.application

import com.acme.order.application.CancelAbandonedOrdersUseCase.Companion.PLACEMENT_ABANDONED
import com.acme.order.domain.Order
import com.acme.order.domain.OrderStatus
import com.acme.order.infrastructure.cart.CartClient
import com.acme.order.infrastructure.cart.CartConversion
import com.acme.order.infrastructure.cart.CartResult
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
    private val cartClient = mockk<CartClient>()
    private val submitter = mockk<OrderSubmitter>()
    private val meterRegistry = SimpleMeterRegistry()
    private val now = Instant.parse("2026-11-01T12:00:00Z")

    private fun useCase(maxAge: Duration = Duration.ofHours(1), batchSize: Int = 100) =
        CancelAbandonedOrdersUseCase(orders, cartClient, submitter, transactionTemplate, meterRegistry, maxAge, batchSize)

    private fun pendingOrder(status: OrderStatus = OrderStatus.PENDING, id: UUID = UUID.randomUUID()) = Order(
        id, "ACME-20261101-000001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "key-1",
        status, BigDecimal("159.98"), "USD", createdAt = now.minus(Duration.ofHours(2))
    )

    private val notConverted = CartResult.Refused(CartClient.CART_NOT_CONVERTED, "Not converted")

    private fun cartNotConverted() {
        every { cartClient.getConversion(any(), any()) } returns notConverted
    }

    private fun recoveredCount() = meterRegistry.find("orders_recovered_total").counter()?.count() ?: 0.0

    private fun cancelledCount() =
        meterRegistry.find("orders_cancelled_total").tag("reason", PLACEMENT_ABANDONED).counter()?.count() ?: 0.0

    @Test
    fun `cancels orders older than the max age with its own code and counts them`() {
        val abandoned = listOf(pendingOrder(), pendingOrder())
        every { orders.findAbandoned(now.minus(Duration.ofHours(1)), Instant.EPOCH, UUID(0, 0), any()) } returns abandoned
        cartNotConverted()
        every { orders.markCancelled(any(), PLACEMENT_ABANDONED, now) } returns 1

        assertEquals(2, useCase().execute(now))

        abandoned.forEach { order -> verify { orders.markCancelled(order.id, PLACEMENT_ABANDONED, now) } }
        assertEquals(2.0, cancelledCount())
    }

    @Test
    fun `an order a retry submitted first is not counted`() {
        val submittedFirst = pendingOrder()
        val abandoned = pendingOrder()
        every { orders.findAbandoned(any(), any(), any(), any()) } returns listOf(submittedFirst, abandoned)
        cartNotConverted()
        every { orders.markCancelled(submittedFirst.id, any(), any()) } returns 0
        every { orders.markCancelled(abandoned.id, any(), any()) } returns 1

        assertEquals(1, useCase().execute(now))

        assertEquals(1.0, cancelledCount())
    }

    @Test
    fun `an order whose cart converted for it is submitted, not cancelled`() {
        val lost = pendingOrder()
        every { orders.findAbandoned(any(), any(), any(), any()) } returns listOf(lost)
        every { cartClient.getConversion(lost.cartId, lost.id) } returns
            CartResult.Ok(CartConversion(lost.cartId, lost.id, now))
        every { submitter.submit(lost) } returns SubmitResult.Submitted(pendingOrder(OrderStatus.SUBMITTED, lost.id))

        assertEquals(0, useCase().execute(now))

        verify { submitter.submit(lost) }
        verify(exactly = 0) { orders.markCancelled(any(), any(), any()) }
        assertEquals(1.0, recoveredCount())
        assertEquals(0.0, cancelledCount())
    }

    @Test
    fun `an order whose cart cannot be asked stays PENDING for the next run`() {
        val unknown = pendingOrder()
        every { orders.findAbandoned(any(), any(), any(), any()) } returns listOf(unknown)
        every { cartClient.getConversion(any(), any()) } returns CartResult.Unavailable

        assertEquals(0, useCase().execute(now))

        verify(exactly = 0) { orders.markCancelled(any(), any(), any()) }
        verify(exactly = 0) { submitter.submit(any()) }
        assertEquals(0.0, cancelledCount())
        assertEquals(0.0, recoveredCount())
    }

    @Test
    fun `an unavailable cart does not stop the rest of the batch`() {
        val unknown = pendingOrder()
        val abandoned = pendingOrder()
        every { orders.findAbandoned(any(), any(), any(), any()) } returns listOf(unknown, abandoned)
        every { cartClient.getConversion(unknown.cartId, any()) } returns CartResult.Unavailable
        every { cartClient.getConversion(abandoned.cartId, any()) } returns notConverted
        every { orders.markCancelled(abandoned.id, any(), any()) } returns 1

        assertEquals(1, useCase().execute(now))
    }

    @Test
    fun `an order the cart refuses the token for stays PENDING`() {
        val unknown = pendingOrder()
        every { orders.findAbandoned(any(), any(), any(), any()) } returns listOf(unknown)
        every { cartClient.getConversion(any(), any()) } returns CartResult.Unauthorized("INVALID_TOKEN")

        assertEquals(0, useCase().execute(now))

        verify(exactly = 0) { orders.markCancelled(any(), any(), any()) }
    }

    @Test
    fun `an order the cart refuses with any other code stays PENDING`() {
        val unknown = pendingOrder()
        every { orders.findAbandoned(any(), any(), any(), any()) } returns listOf(unknown)
        every { cartClient.getConversion(any(), any()) } returns CartResult.Refused("CART_NOT_FOUND", "Cart not found")

        assertEquals(0, useCase().execute(now))

        verify(exactly = 0) { orders.markCancelled(any(), any(), any()) }
        verify(exactly = 0) { submitter.submit(any()) }
        assertEquals(0.0, cancelledCount())
    }

    @Test
    fun `an order another caller submitted first is not counted as recovered`() {
        val lost = pendingOrder()
        every { orders.findAbandoned(any(), any(), any(), any()) } returns listOf(lost)
        every { cartClient.getConversion(lost.cartId, lost.id) } returns
            CartResult.Ok(CartConversion(lost.cartId, lost.id, now))
        every { submitter.submit(lost) } returns SubmitResult.AlreadySubmitted(pendingOrder(OrderStatus.SUBMITTED, lost.id))

        assertEquals(0, useCase().execute(now))

        assertEquals(0.0, recoveredCount())
        assertEquals(0.0, cancelledCount())
    }

    @Test
    fun `an order a racing caller cancelled before it could be submitted is not counted as recovered`() {
        val lost = pendingOrder()
        every { orders.findAbandoned(any(), any(), any(), any()) } returns listOf(lost)
        every { cartClient.getConversion(lost.cartId, lost.id) } returns
            CartResult.Ok(CartConversion(lost.cartId, lost.id, now))
        every { submitter.submit(lost) } returns SubmitResult.Cancelled(pendingOrder(OrderStatus.CANCELLED, lost.id))

        assertEquals(0, useCase().execute(now))

        assertEquals(0.0, recoveredCount())
        assertEquals(0.0, cancelledCount())
    }

    @Test
    fun `an order left PENDING does not hide the orders behind it, the next page starts after it`() {
        val stuckA = pendingOrder()
        val stuckB = pendingOrder()
        val behind = pendingOrder()
        every { orders.findAbandoned(any(), Instant.EPOCH, UUID(0, 0), any()) } returns listOf(stuckA, stuckB)
        every { orders.findAbandoned(any(), stuckB.createdAt, stuckB.id, any()) } returns listOf(behind)
        every { cartClient.getConversion(stuckA.cartId, any()) } returns CartResult.Refused("CART_NOT_FOUND", "x")
        every { cartClient.getConversion(stuckB.cartId, any()) } returns CartResult.Refused("CART_NOT_FOUND", "x")
        every { cartClient.getConversion(behind.cartId, any()) } returns notConverted
        every { orders.markCancelled(behind.id, any(), any()) } returns 1

        assertEquals(1, useCase(batchSize = 2).execute(now))

        verify { orders.markCancelled(behind.id, PLACEMENT_ABANDONED, now) }
    }

    @Test
    fun `a page the cart could not answer at all ends the run`() {
        val first = pendingOrder()
        val second = pendingOrder()
        every { orders.findAbandoned(any(), any(), any(), any()) } returns listOf(first, second)
        every { cartClient.getConversion(any(), any()) } returns CartResult.Unavailable

        assertEquals(0, useCase(batchSize = 2).execute(now))

        verify(exactly = 1) { orders.findAbandoned(any(), any(), any(), any()) }
    }

    @Test
    fun `a run reads at most MAX_PAGES pages`() {
        val skipped = pendingOrder()
        every { orders.findAbandoned(any(), any(), any(), any()) } returns listOf(skipped)
        every { cartClient.getConversion(any(), any()) } returns CartResult.Refused("CART_NOT_FOUND", "x")

        assertEquals(0, useCase(batchSize = 1).execute(now))

        verify(exactly = CancelAbandonedOrdersUseCase.MAX_PAGES) { orders.findAbandoned(any(), any(), any(), any()) }
    }

    @Test
    fun `one order failing is skipped and the rest of the batch is settled`() {
        val failing = pendingOrder()
        val abandoned = pendingOrder()
        every { orders.findAbandoned(any(), any(), any(), any()) } returns listOf(failing, abandoned)
        every { cartClient.getConversion(failing.cartId, any()) } throws IllegalStateException("boom")
        every { cartClient.getConversion(abandoned.cartId, any()) } returns notConverted
        every { orders.markCancelled(abandoned.id, any(), any()) } returns 1

        assertEquals(1, useCase().execute(now))

        verify(exactly = 0) { orders.markCancelled(failing.id, any(), any()) }
    }

    @Test
    fun `a submit that fails leaves the order PENDING and the rest of the batch goes on`() {
        val lost = pendingOrder()
        val abandoned = pendingOrder()
        every { orders.findAbandoned(any(), any(), any(), any()) } returns listOf(lost, abandoned)
        every { cartClient.getConversion(lost.cartId, lost.id) } returns CartResult.Ok(CartConversion(lost.cartId, lost.id, now))
        every { submitter.submit(lost) } throws IllegalStateException("outbox down")
        every { cartClient.getConversion(abandoned.cartId, any()) } returns notConverted
        every { orders.markCancelled(abandoned.id, any(), any()) } returns 1

        assertEquals(1, useCase().execute(now))

        assertEquals(0.0, recoveredCount())
    }

    @Test
    fun `a run with nothing abandoned reads one page of the batch size`() {
        val page = slot<Pageable>()
        every { orders.findAbandoned(any(), any(), any(), capture(page)) } returns emptyList()

        assertEquals(0, useCase(batchSize = 7).execute(now))

        assertEquals(7, page.captured.pageSize)
        verify(exactly = 1) { orders.findAbandoned(any(), any(), any(), any()) }
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
