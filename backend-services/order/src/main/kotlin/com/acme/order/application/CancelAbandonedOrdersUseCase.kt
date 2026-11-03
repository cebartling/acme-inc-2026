package com.acme.order.application

import com.acme.order.domain.Order
import com.acme.order.domain.OrderStatus
import com.acme.order.infrastructure.cart.CartClient
import com.acme.order.infrastructure.cart.CartResult
import com.acme.order.infrastructure.persistence.OrderRepository
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Settles orders left PENDING because the customer never retried (PIN-427, ADR-0062, PIN-491).
 *
 * Before cancelling, it asks the cart whether it converted for the order, with the service
 * token alone since there is no customer token here (ADR-0061). If it did, the convert's answer
 * was lost, and the order is submitted instead, through [OrderSubmitter]. If the cart says it
 * didn't, the order is cancelled by the conditional `OrderRepository.markCancelled`, so a retry
 * submitting it at the same moment leaves it either SUBMITTED or CANCELLED. If the cart can't
 * be asked, the order stays PENDING for the next run: cancelling on a guess could lose a
 * converted cart. A run pages through the abandoned orders oldest first, [batchSize] at a time
 * and at most [MAX_PAGES] pages, so orders it leaves PENDING can't hide the ones behind them. It
 * stops early when a whole page couldn't reach the cart, since the cart is then down or
 * refusing the token, and walking the backlog would only wait out timeouts. One order failing
 * is logged and skipped; it doesn't stop the rest. What's left waits for the next run.
 *
 * The check narrows the race with a retry but doesn't close it: a retry can convert the cart
 * between the check and the cancel, and the customer then has no cart and no order. Every
 * cancel is logged and counted for that reason, and so is a crash between convert and submit
 * when the cart has since been deleted.
 */
@Service
class CancelAbandonedOrdersUseCase(
    private val orders: OrderRepository,
    private val cartClient: CartClient,
    private val submitter: OrderSubmitter,
    private val transactionTemplate: TransactionTemplate,
    meterRegistry: MeterRegistry,
    @Value("\${acme.order.abandoned.max-age}") private val maxAge: Duration,
    @Value("\${acme.order.abandoned.batch-size}") private val batchSize: Int
) {
    private val logger = LoggerFactory.getLogger(CancelAbandonedOrdersUseCase::class.java)
    private val abandonedOrders = meterRegistry.counter("orders_cancelled_total", "reason", PLACEMENT_ABANDONED)
    private val recoveredOrders = meterRegistry.counter("orders_recovered_total", "reason", LOST_CONVERT)

    init {
        require(maxAge > MINIMUM_MAX_AGE) {
            "acme.order.abandoned.max-age must be longer than $MINIMUM_MAX_AGE, the cart's checkout " +
                "session plus its unlock grace and interval, was $maxAge"
        }
        require(batchSize >= 1) { "acme.order.abandoned.batch-size must be at least 1, was $batchSize" }
    }

    /**
     * Settles orders PENDING for longer than [maxAge], and returns how many it cancelled. Orders
     * it submitted or left PENDING aren't counted.
     */
    fun execute(now: Instant = Instant.now()): Int {
        val cutoff = now.minus(maxAge)
        var cancelled = 0
        var afterCreatedAt = Instant.EPOCH
        var afterId = UUID(0, 0)
        repeat(MAX_PAGES) {
            val page = orders.findAbandoned(cutoff, afterCreatedAt, afterId, PageRequest.of(0, batchSize))
            val outcomes = page.map { settleIsolated(it, now) }
            cancelled += outcomes.count { it == Outcome.CANCELLED }
            val cartUnreachable = page.isNotEmpty() && outcomes.all { it == Outcome.CART_UNREACHABLE }
            if (page.size < batchSize || cartUnreachable) return cancelled
            afterCreatedAt = page.last().createdAt
            afterId = page.last().id
        }
        return cancelled
    }

    private enum class Outcome { CANCELLED, RECOVERED, SKIPPED, CART_UNREACHABLE }

    /** One order's failure is logged and leaves it PENDING for the next run; the batch goes on. */
    private fun settleIsolated(order: Order, now: Instant): Outcome =
        try {
            settle(order, now)
        } catch (ex: RuntimeException) {
            logger.error(
                "Failed to settle abandoned order {} ({}) for cart {}; leaving it PENDING",
                order.id, order.orderNumber, order.cartId, ex
            )
            Outcome.SKIPPED
        }

    private fun settle(order: Order, now: Instant): Outcome =
        when (val conversion = cartClient.getConversion(order.cartId, order.id)) {
            is CartResult.Ok -> {
                recover(order)
                Outcome.RECOVERED
            }
            // Only this answer says the cart didn't convert; any other refusal is not a reason to cancel
            is CartResult.Refused ->
                if (conversion.code == CartClient.CART_NOT_CONVERTED) {
                    if (cancel(order, now)) Outcome.CANCELLED else Outcome.SKIPPED
                } else {
                    leavePending(order, conversion)
                    Outcome.SKIPPED
                }
            is CartResult.Unauthorized, CartResult.Unavailable -> {
                leavePending(order, conversion)
                Outcome.CART_UNREACHABLE
            }
        }

    private fun leavePending(order: Order, answer: CartResult<*>) {
        logger.warn(
            "Could not ask cart {} whether it converted for abandoned order {} ({}): {}; leaving it PENDING",
            order.cartId, order.id, order.orderNumber, answer
        )
    }

    private fun recover(order: Order) {
        val submitted = submitter.submit(order)
        if (submitted.status == OrderStatus.SUBMITTED) {
            logger.warn(
                "Cart {} converted for abandoned order {} ({}), PENDING since {}; submitted it for user {}",
                order.cartId, order.id, order.orderNumber, order.createdAt, order.userId
            )
            recoveredOrders.increment()
        } else {
            logger.error(
                "Cart {} converted for order {} ({}), which was cancelled with {}: user {} has no cart and no order",
                order.cartId, order.id, order.orderNumber, submitted.cancelCode, order.userId
            )
        }
    }

    private fun cancel(order: Order, now: Instant): Boolean {
        val cancelled = transactionTemplate.execute { orders.markCancelled(order.id, PLACEMENT_ABANDONED, now) } == 1
        if (cancelled) {
            logger.warn(
                "Cancelled abandoned order {} ({}) for user {}, PENDING since {}; its cart {} did not convert " +
                    "for it, unless a retry converted it just now",
                order.id, order.orderNumber, order.userId, order.createdAt, order.cartId
            )
            abandonedOrders.increment()
        }
        return cancelled
    }

    companion object {
        const val PLACEMENT_ABANDONED = "PLACEMENT_ABANDONED"
        const val LOST_CONVERT = "LOST_CONVERT"

        /** Bounds one run: at most this many pages of `batch-size` orders. */
        const val MAX_PAGES = 10

        /**
         * The cart's 30-minute checkout session, 2-minute unlock grace and 1-minute unlock
         * interval. The order service can't read the cart's settings, so these are its defaults.
         */
        val MINIMUM_MAX_AGE: Duration = Duration.ofMinutes(33)
    }
}
