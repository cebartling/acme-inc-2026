package com.acme.order.application

import com.acme.order.domain.Order
import com.acme.order.domain.OrderStatus
import com.acme.order.infrastructure.persistence.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Finds the order a Place order request saved, without placing anything (PIN-440), for a client
 * that never heard the answer. Unlike [GetOrderUseCase] it shows a PENDING order, which that
 * client may need to resume. A CANCELLED one is treated as no order, because none was placed.
 */
@Service
class FindOrderByKeyUseCase(private val orders: OrderRepository) {

    @Transactional(readOnly = true)
    fun execute(userId: UUID, idempotencyKey: String): Order? =
        orders.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
            ?.takeUnless { it.status == OrderStatus.CANCELLED }
}
