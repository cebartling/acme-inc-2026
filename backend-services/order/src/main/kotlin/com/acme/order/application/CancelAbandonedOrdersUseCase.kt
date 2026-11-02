package com.acme.order.application

import com.acme.order.domain.Order
import com.acme.order.infrastructure.persistence.OrderRepository
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant

/**
 * Cancels orders left PENDING because the customer never retried (PIN-427, ADR-0062).
 *
 * It can only cancel: converting the cart needs the customer's access token, so placing the
 * order can't be finished here. Each order is cancelled by the conditional
 * `OrderRepository.markCancelled`, so a retry submitting it at the same moment leaves it either
 * SUBMITTED or CANCELLED. Each run takes one batch; what's left waits for the next run.
 *
 * Every cancel is logged and counted, because an order can be PENDING with its cart already
 * converted: the service crashed between the two, or the convert committed but its answer
 * timed out and no retry came. That customer has no cart and no order, and this job can't tell,
 * so support has to follow up.
 */
@Service
class CancelAbandonedOrdersUseCase(
    private val orders: OrderRepository,
    private val transactionTemplate: TransactionTemplate,
    meterRegistry: MeterRegistry,
    @Value("\${acme.order.abandoned.max-age}") private val maxAge: Duration,
    @Value("\${acme.order.abandoned.batch-size}") private val batchSize: Int
) {
    private val logger = LoggerFactory.getLogger(CancelAbandonedOrdersUseCase::class.java)
    private val abandonedOrders = meterRegistry.counter("orders_cancelled_total", "reason", PLACEMENT_ABANDONED)

    init {
        require(maxAge > MINIMUM_MAX_AGE) {
            "acme.order.abandoned.max-age must be longer than $MINIMUM_MAX_AGE, the cart's checkout " +
                "session plus its unlock grace and interval, was $maxAge"
        }
        require(batchSize >= 1) { "acme.order.abandoned.batch-size must be at least 1, was $batchSize" }
    }

    /** Cancels up to one batch of orders PENDING for longer than [maxAge], and returns how many. */
    fun execute(now: Instant = Instant.now()): Int =
        orders.findAbandoned(now.minus(maxAge), PageRequest.of(0, batchSize)).count { cancel(it, now) }

    private fun cancel(order: Order, now: Instant): Boolean {
        val cancelled = transactionTemplate.execute { orders.markCancelled(order.id, PLACEMENT_ABANDONED, now) } == 1
        if (cancelled) {
            logger.warn(
                "Cancelled abandoned order {} ({}) for user {}, PENDING since {}; if its cart {} was " +
                    "converted, the customer has no cart and no order",
                order.id, order.orderNumber, order.userId, order.createdAt, order.cartId
            )
            abandonedOrders.increment()
        }
        return cancelled
    }

    companion object {
        const val PLACEMENT_ABANDONED = "PLACEMENT_ABANDONED"

        /**
         * The cart's 30-minute checkout session, 2-minute unlock grace and 1-minute unlock
         * interval. The order service can't read the cart's settings, so these are its defaults.
         */
        val MINIMUM_MAX_AGE: Duration = Duration.ofMinutes(33)
    }
}
