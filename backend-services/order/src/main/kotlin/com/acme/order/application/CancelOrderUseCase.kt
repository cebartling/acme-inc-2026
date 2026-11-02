package com.acme.order.application

import com.acme.order.domain.OrderStatus
import com.acme.order.domain.events.OrderCancelled
import com.acme.order.domain.events.OrderCancelledPayload
import com.acme.order.infrastructure.messaging.OutboxWriter
import com.acme.order.infrastructure.persistence.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

/**
 * A customer cancels their SUBMITTED order (PIN-429, ADR-0063). Only the request whose update
 * moved the order writes `OrderCancelled`, so a repeat or a racing request writes no second
 * event and still answers with the cancelled order. The reason is free text from the customer,
 * so it isn't logged.
 */
@Service
class CancelOrderUseCase(
    private val orders: OrderRepository,
    private val getOrder: GetOrderUseCase,
    private val outboxWriter: OutboxWriter,
    private val transactionTemplate: TransactionTemplate
) {
    private val logger = LoggerFactory.getLogger(CancelOrderUseCase::class.java)

    fun execute(orderId: UUID, userId: UUID, reason: String): GetOrderResult {
        transactionTemplate.executeWithoutResult {
            val now = Instant.now()
            if (orders.markCancelledByCustomer(orderId, userId, reason, now) == 1) {
                val order = orders.findById(orderId).get()
                outboxWriter.write(
                    OrderCancelled.create(
                        OrderCancelledPayload(order.id, order.orderNumber, order.userId, reason),
                        correlationId = order.checkoutSessionId,
                        now = now
                    )
                )
                logger.info("Order {} ({}) cancelled by its customer", order.id, order.orderNumber)
            }
        }
        // A PENDING order that was SUBMITTED after the update above is still SUBMITTED: not cancelled
        val result = getOrder.execute(orderId, userId)
        return if (result is GetOrderResult.Found && result.order.status == OrderStatus.CANCELLED) {
            result
        } else {
            GetOrderResult.NotFound
        }
    }
}
