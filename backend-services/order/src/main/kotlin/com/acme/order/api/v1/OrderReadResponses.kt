package com.acme.order.api.v1

import com.acme.order.application.OrderDetails
import com.acme.order.infrastructure.persistence.OrderSummary
import org.springframework.data.domain.Page
import java.time.Instant
import java.util.UUID

data class OrderLineResponse(
    val lineItemId: UUID,
    val variantId: UUID,
    val productId: String?,
    val productName: String?,
    val variantName: String?,
    val sku: String?,
    val imageUrl: String?,
    val quantity: Int,
    val unitPrice: MoneyResponse,
    val lineTotal: MoneyResponse
)

data class OrderPricingResponse(val subtotal: MoneyResponse)

/** Journey 0005 step 6's "Get Order Details", cut down to what exists (PIN-367). */
data class OrderDetailsResponse(
    val orderId: UUID,
    val orderNumber: String,
    val status: String,
    val createdAt: Instant,
    val lineItems: List<OrderLineResponse>,
    val pricing: OrderPricingResponse
) {
    companion object {
        fun from(order: OrderDetails) = OrderDetailsResponse(
            orderId = order.id,
            orderNumber = order.orderNumber,
            status = order.status.name,
            createdAt = order.createdAt,
            lineItems = order.lines.map {
                OrderLineResponse(
                    lineItemId = it.id,
                    variantId = it.variantId,
                    productId = it.productId,
                    productName = it.productName,
                    variantName = it.variantName,
                    sku = it.sku,
                    imageUrl = it.imageUrl,
                    quantity = it.quantity,
                    unitPrice = MoneyResponse(it.unitPrice, order.currency),
                    lineTotal = MoneyResponse(it.lineTotal, order.currency)
                )
            },
            pricing = OrderPricingResponse(MoneyResponse(order.subtotal, order.currency))
        )
    }
}

data class OrderSummaryItemResponse(
    val orderId: UUID,
    val orderNumber: String,
    val status: String,
    val createdAt: Instant,
    val itemCount: Long,
    val subtotal: MoneyResponse
)

/** A page of order history; the paging fields are the product search's. */
data class OrderListResponse(
    val orders: List<OrderSummaryItemResponse>,
    val page: Int,
    val pageSize: Int,
    val totalResults: Long,
    val totalPages: Int
) {
    companion object {
        fun from(page: Page<OrderSummary>) = OrderListResponse(
            orders = page.content.map {
                OrderSummaryItemResponse(
                    orderId = it.id,
                    orderNumber = it.orderNumber,
                    status = it.status.name,
                    createdAt = it.createdAt,
                    itemCount = it.itemCount,
                    subtotal = MoneyResponse(it.subtotal, it.currency)
                )
            },
            page = page.number + 1,
            pageSize = page.size,
            totalResults = page.totalElements,
            totalPages = page.totalPages
        )
    }
}
