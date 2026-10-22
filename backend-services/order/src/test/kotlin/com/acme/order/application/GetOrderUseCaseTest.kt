package com.acme.order.application

import com.acme.order.config.JacksonConfig
import com.acme.order.domain.Order
import com.acme.order.domain.OrderStatus
import com.acme.order.infrastructure.persistence.OrderRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class GetOrderUseCaseTest {

    private val orders = mockk<OrderRepository>()
    private val useCase = GetOrderUseCase(
        orders, JacksonConfig().objectMapper(), mockk<PlatformTransactionManager>(relaxed = true)
    )
    private val userId = UUID.randomUUID()
    private val variantId = UUID.randomUUID()

    private val order = Order(
        id = UUID.randomUUID(),
        orderNumber = "ACME-20261022-000001",
        userId = userId,
        checkoutSessionId = UUID.randomUUID(),
        cartId = UUID.randomUUID(),
        idempotencyKey = "key-1",
        status = OrderStatus.SUBMITTED,
        subtotal = BigDecimal("139.98"),
        currency = "USD",
        createdAt = Instant.parse("2026-10-22T12:00:00Z")
    ).apply {
        addItem(
            UUID.randomUUID(), variantId, 2, BigDecimal("69.99"), BigDecimal("139.98"),
            """{"productId":"p-1","name":"Gadget Pro","sku":"GP-BLK","variantName":"Black","imageUrl":null,"attributes":{}}"""
        )
    }

    @Test
    fun `maps the order and its lines, with product details from the snapshot`() {
        every { orders.findByIdAndUserIdAndStatus(order.id, userId, OrderStatus.SUBMITTED) } returns order

        val details = (useCase.execute(order.id, userId) as GetOrderResult.Found).order

        assertEquals("ACME-20261022-000001", details.orderNumber)
        assertEquals(BigDecimal("139.98"), details.subtotal)
        val line = details.lines.single()
        assertEquals(variantId, line.variantId)
        assertEquals("p-1", line.productId)
        assertEquals("Gadget Pro", line.productName)
        assertEquals("Black", line.variantName)
        assertEquals("GP-BLK", line.sku)
        assertNull(line.imageUrl)
        assertEquals(2, line.quantity)
    }

    @Test
    fun `an order that isn't the caller's SUBMITTED order is not found`() {
        every { orders.findByIdAndUserIdAndStatus(any(), any(), any()) } returns null

        assertEquals(GetOrderResult.NotFound, useCase.execute(order.id, userId))
    }
}
