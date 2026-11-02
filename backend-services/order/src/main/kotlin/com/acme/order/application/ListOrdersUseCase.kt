package com.acme.order.application

import com.acme.order.infrastructure.persistence.OrderRepository
import com.acme.order.infrastructure.persistence.OrderSummary
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * The caller's order history (PIN-367, the start of epic F5): SUBMITTED orders and those they
 * cancelled, newest first. `page` is 1-based, like the product search's.
 */
@Service
class ListOrdersUseCase(private val orders: OrderRepository) {

    fun execute(userId: UUID, page: Int, pageSize: Int): Page<OrderSummary> {
        require(page >= 1 && pageSize >= 1) { "page and pageSize must be at least 1, got $page and $pageSize" }
        return orders.findSummaries(userId, PageRequest.of(page - 1, pageSize))
    }
}
