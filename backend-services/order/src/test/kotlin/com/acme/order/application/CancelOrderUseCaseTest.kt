package com.acme.order.application

import com.acme.order.domain.Order
import com.acme.order.domain.OrderStatus
import com.acme.order.domain.events.DomainEvent
import com.acme.order.domain.events.OrderCancelled
import com.acme.order.domain.events.OrderCancelledPayload
import com.acme.order.infrastructure.messaging.OutboxWriter
import com.acme.order.infrastructure.persistence.OrderRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

class CancelOrderUseCaseTest {

    private val orders = mockk<OrderRepository>()
    private val getOrder = mockk<GetOrderUseCase>()
    private val outboxWriter = mockk<OutboxWriter>(relaxed = true)
    private val useCase = CancelOrderUseCase(
        orders, getOrder, outboxWriter, TransactionTemplate(mockk<PlatformTransactionManager>(relaxed = true))
    )

    private val userId = UUID.randomUUID()
    private val order = Order(
        id = UUID.randomUUID(),
        orderNumber = "ACME-20261101-000001",
        userId = userId,
        checkoutSessionId = UUID.randomUUID(),
        cartId = UUID.randomUUID(),
        idempotencyKey = "key-1",
        status = OrderStatus.CANCELLED,
        subtotal = BigDecimal("139.98"),
        currency = "USD",
        createdAt = Instant.parse("2026-11-01T12:00:00Z")
    )

    private fun found(status: OrderStatus) = GetOrderResult.Found(
        OrderDetails(order.id, order.orderNumber, status, order.createdAt, order.subtotal, order.currency, emptyList())
    )

    @Test
    fun `cancelling a submitted order writes OrderCancelled and answers the cancelled order`() {
        val event = slot<DomainEvent>()
        every { orders.markCancelledByCustomer(order.id, userId, "Changed my mind", any()) } returns 1
        every { orders.findById(order.id) } returns Optional.of(order)
        every { outboxWriter.write(capture(event)) } returns Unit
        every { getOrder.execute(order.id, userId) } returns found(OrderStatus.CANCELLED)

        val result = useCase.execute(order.id, userId, "Changed my mind")

        assertEquals(found(OrderStatus.CANCELLED), result)
        val written = event.captured as OrderCancelled
        assertEquals(OrderCancelledPayload(order.id, order.orderNumber, userId, "Changed my mind"), written.payload)
        assertEquals(order.id, written.aggregateId)
        assertEquals(order.checkoutSessionId, written.correlationId)
    }

    @Test
    fun `cancelling again answers the cancelled order and writes no second event`() {
        every { orders.markCancelledByCustomer(any(), any(), any(), any()) } returns 0
        every { getOrder.execute(order.id, userId) } returns found(OrderStatus.CANCELLED)

        val result = useCase.execute(order.id, userId, "Changed my mind")

        assertEquals(found(OrderStatus.CANCELLED), result)
        verify(exactly = 0) { outboxWriter.write(any()) }
    }

    @Test
    fun `an order the caller can't see is not found and writes no event`() {
        every { orders.markCancelledByCustomer(any(), any(), any(), any()) } returns 0
        every { getOrder.execute(order.id, userId) } returns GetOrderResult.NotFound

        assertEquals(GetOrderResult.NotFound, useCase.execute(order.id, userId, "Changed my mind"))
        verify(exactly = 0) { outboxWriter.write(any()) }
    }

    @Test
    fun `a pending order submitted just after the update is not found, not reported as cancelled`() {
        every { orders.markCancelledByCustomer(any(), any(), any(), any()) } returns 0
        every { getOrder.execute(order.id, userId) } returns found(OrderStatus.SUBMITTED)

        assertEquals(GetOrderResult.NotFound, useCase.execute(order.id, userId, "Changed my mind"))
    }
}
