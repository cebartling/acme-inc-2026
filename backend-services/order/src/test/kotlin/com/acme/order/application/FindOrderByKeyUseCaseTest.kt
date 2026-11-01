package com.acme.order.application

import com.acme.order.domain.Order
import com.acme.order.domain.OrderStatus
import com.acme.order.infrastructure.persistence.OrderRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class FindOrderByKeyUseCaseTest {

    private val orders = mockk<OrderRepository>()
    private val useCase = FindOrderByKeyUseCase(orders)
    private val userId = UUID.randomUUID()

    private fun orderIn(status: OrderStatus) = Order(
        id = UUID.randomUUID(),
        orderNumber = "ACME-20261101-000001",
        userId = userId,
        checkoutSessionId = UUID.randomUUID(),
        cartId = UUID.randomUUID(),
        idempotencyKey = "key-1",
        status = status,
        subtotal = BigDecimal("139.98"),
        currency = "USD",
        createdAt = Instant.parse("2026-11-01T12:00:00Z")
    )

    @ParameterizedTest
    @EnumSource(value = OrderStatus::class, names = ["PENDING", "SUBMITTED"])
    fun `finds an order that was saved or placed`(status: OrderStatus) {
        val order = orderIn(status)
        every { orders.findByUserIdAndIdempotencyKey(userId, "key-1") } returns order

        assertEquals(order, useCase.execute(userId, "key-1"))
    }

    @Test
    fun `a cancelled order counts as no order`() {
        every { orders.findByUserIdAndIdempotencyKey(userId, "key-1") } returns orderIn(OrderStatus.CANCELLED)

        assertNull(useCase.execute(userId, "key-1"))
    }

    @Test
    fun `a key with no order finds nothing`() {
        every { orders.findByUserIdAndIdempotencyKey(userId, "key-1") } returns null

        assertNull(useCase.execute(userId, "key-1"))
    }
}
