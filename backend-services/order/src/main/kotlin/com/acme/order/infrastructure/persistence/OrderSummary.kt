package com.acme.order.infrastructure.persistence

import com.acme.order.domain.OrderStatus
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * One row of a customer's order history (PIN-367), read by `OrderRepository.findSummaries`
 * in one query, so listing orders never loads their lines.
 */
data class OrderSummary(
    val id: UUID,
    val orderNumber: String,
    val status: OrderStatus,
    val createdAt: Instant,
    val subtotal: BigDecimal,
    val currency: String,
    /** Units across the order's lines. */
    val itemCount: Long
)
