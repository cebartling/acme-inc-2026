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
     * announce it once. The result says what happened: this call submitted it, another caller
     * had already, or the order is CANCELLED (which is logged here, since the cart has converted
     * and the customer is left with no cart and no order).
     */
    fun submit(order: Order): SubmitResult = transactionTemplate.execute {
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
            return@execute SubmitResult.Submitted(current)
        }
        when (current.status) {
            OrderStatus.CANCELLED -> {
                logger.error(
                    "Cart {} converted for order {} ({}), which was cancelled with {}: user {} has no cart and no order",
                    current.cartId, current.id, current.orderNumber, current.cancelCode, current.userId
                )
                SubmitResult.Cancelled(current)
            }
            else -> SubmitResult.AlreadySubmitted(current)
        }
    }!!
}

/** What [OrderSubmitter.submit] found, and whether this call is the one that submitted the order. */
sealed interface SubmitResult {
    val order: Order

    /** This call moved the order from PENDING to SUBMITTED. */
    data class Submitted(override val order: Order) : SubmitResult

    /** Another caller had already submitted it; this call changed nothing. */
    data class AlreadySubmitted(override val order: Order) : SubmitResult

    /** The order is CANCELLED, so it was not submitted. */
    data class Cancelled(override val order: Order) : SubmitResult
}
