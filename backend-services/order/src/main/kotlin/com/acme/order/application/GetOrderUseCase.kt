package com.acme.order.application

import com.acme.order.domain.Order
import com.acme.order.domain.OrderStatus
import com.acme.order.infrastructure.persistence.OrderRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** An order as its owner reads it (PIN-367), mapped while its lines are still loadable. */
data class OrderDetails(
    val id: UUID,
    val orderNumber: String,
    val status: OrderStatus,
    val createdAt: Instant,
    val subtotal: BigDecimal,
    val currency: String,
    val lines: List<OrderLine>
)

/** One line, with the product details the cart's snapshot recorded when the order was placed. */
data class OrderLine(
    val id: UUID,
    val variantId: UUID,
    val productId: String?,
    val productName: String?,
    val variantName: String?,
    val sku: String?,
    val imageUrl: String?,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val lineTotal: BigDecimal
)

sealed interface GetOrderResult {
    data class Found(val order: OrderDetails) : GetOrderResult

    /** Missing, someone else's, or not SUBMITTED: the caller can't tell which. */
    data object NotFound : GetOrderResult
}

/**
 * Reads one of the caller's orders (PIN-367, journey 0005 step 6). Only SUBMITTED orders are
 * visible: PENDING and CANCELLED ones are placement attempts the customer was told didn't
 * succeed.
 */
@Service
class GetOrderUseCase(
    private val orders: OrderRepository,
    private val objectMapper: ObjectMapper,
    transactionManager: PlatformTransactionManager
) {
    private val readOnly = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    fun execute(orderId: UUID, userId: UUID): GetOrderResult =
        readOnly.execute {
            orders.findByIdAndUserIdAndStatus(orderId, userId, OrderStatus.SUBMITTED)
                ?.let { GetOrderResult.Found(details(it)) }
                ?: GetOrderResult.NotFound
        }!!

    private fun details(order: Order) = OrderDetails(
        id = order.id,
        orderNumber = order.orderNumber,
        status = order.status,
        createdAt = order.createdAt,
        subtotal = order.subtotal,
        currency = order.currency,
        lines = order.items.map { item ->
            val snapshot = objectMapper.readValue(item.productSnapshot, SNAPSHOT_TYPE)
            OrderLine(
                id = item.id,
                variantId = item.variantId,
                productId = snapshot["productId"]?.toString(),
                productName = snapshot["name"]?.toString(),
                variantName = snapshot["variantName"]?.toString(),
                sku = snapshot["sku"]?.toString(),
                imageUrl = snapshot["imageUrl"]?.toString(),
                quantity = item.quantity,
                unitPrice = item.unitPrice,
                lineTotal = item.lineTotal
            )
        }
    )

    private companion object {
        val SNAPSHOT_TYPE = object : TypeReference<Map<String, Any?>>() {}
    }
}
