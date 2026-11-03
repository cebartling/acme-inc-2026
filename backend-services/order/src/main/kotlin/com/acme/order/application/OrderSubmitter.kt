package com.acme.order.application

import com.acme.order.domain.Order
import com.acme.order.domain.OrderStatus
import com.acme.order.domain.events.Money
import com.acme.order.domain.events.OrderCreated
import com.acme.order.domain.events.OrderCreatedPayload
import com.acme.order.infrastructure.messaging.OutboxWriter
import com.acme.order.infrastructure.persistence.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

/**
 * Finishes placing an order whose cart has converted: `PENDING → SUBMITTED` and `OrderCreated`
 * to the outbox, in one transaction. Shared by [PlaceOrderUseCase] and, for a convert whose
 * answer was lost, [CancelAbandonedOrdersUseCase] (PIN-491).
 */
@Component
class OrderSubmitter(
    private val orders: OrderRepository,
    private val outboxWriter: OutboxWriter,
    private val transactionTemplate: TransactionTemplate
) {
    private val logger = LoggerFactory.getLogger(OrderSubmitter::class.java)

    /**
     * Writes `OrderCreated` only if this call moved the order out of PENDING, so racing callers
     * announce it once. Returns the order as it now is: SUBMITTED, or CANCELLED if a racing
     * caller cancelled it first.
     */
    fun submit(order: Order): Order = transactionTemplate.execute {
        val now = Instant.now()
        val submittedNow = orders.markSubmitted(order.id, now) == 1
        val current = orders.findById(order.id).get()
        if (submittedNow) {
            outboxWriter.write(
                OrderCreated.create(
                    OrderCreatedPayload(
                        orderId = current.id,
                        orderNumber = current.orderNumber,
                        userId = current.userId,
                        status = OrderStatus.SUBMITTED.name,
                        total = Money(current.subtotal, current.currency),
                        itemCount = current.items.sumOf { it.quantity }
                    ),
                    correlationId = current.checkoutSessionId,
                    now = now
                )
            )
            logger.info("Order {} ({}) SUBMITTED", current.id, current.orderNumber)
        }
        current
    }!!
}
