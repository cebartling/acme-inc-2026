package com.acme.order.application

import com.acme.order.domain.Order
import com.acme.order.domain.OrderStatus
import com.acme.order.domain.events.Money
import com.acme.order.domain.events.OrderCreated
import com.acme.order.domain.events.OrderCreatedPayload
import com.acme.order.infrastructure.cart.CartClient
import com.acme.order.infrastructure.cart.CartResult
import com.acme.order.infrastructure.cart.CheckoutSession
import com.acme.order.infrastructure.messaging.OutboxWriter
import com.acme.order.infrastructure.persistence.OrderIdGenerator
import com.acme.order.infrastructure.persistence.OrderNumberSequenceRepository
import com.acme.order.infrastructure.persistence.OrderRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

data class PlaceOrderCommand(
    val userId: UUID,
    /** The customer's access token, forwarded to the cart as its `access_token` cookie. */
    val accessToken: String,
    val checkoutSessionId: UUID,
    val idempotencyKey: String
)

/** How placing an order ended (ADR-0020). */
sealed interface PlaceOrderResult {
    /** The order is SUBMITTED: newly, or by an earlier request with the same key. */
    data class Placed(val order: Order) : PlaceOrderResult

    /** The order can't be placed; `code` is answered in the `{error, code}` body. */
    data class Refused(val status: HttpStatus, val code: String, val message: String) : PlaceOrderResult

    /** The cart didn't answer. Any saved order stays PENDING, and the same key resumes it. */
    data object CartUnavailable : PlaceOrderResult
}

/**
 * Places an order from a checkout session (PIN-366, journey 0005 step 5).
 *
 * 1. A repeated `Idempotency-Key` finds the earlier order instead (AC-5.8): a SUBMITTED one
 *    is answered again, a CANCELLED one is refused again, and a PENDING one resumes at 4.
 * 2. The checkout session's cart is read from the cart, on the customer's behalf.
 * 3. The order is saved PENDING with the cart's lines.
 * 4. The cart is converted. Convert is safe to repeat for the same order, so a resumed or
 *    racing request can call it again.
 * 5. The order becomes SUBMITTED and `OrderCreated` goes to the outbox, in one transaction,
 *    written only by the request whose update moved the order out of PENDING.
 *
 * A final refusal from the cart cancels the order without announcing it. The cart calls
 * happen outside any transaction.
 */
@Service
class PlaceOrderUseCase(
    private val orders: OrderRepository,
    private val orderNumbers: OrderNumberSequenceRepository,
    private val ids: OrderIdGenerator,
    private val cartClient: CartClient,
    private val outboxWriter: OutboxWriter,
    private val transactionTemplate: TransactionTemplate,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(PlaceOrderUseCase::class.java)

    fun execute(command: PlaceOrderCommand): PlaceOrderResult {
        orders.findByUserIdAndIdempotencyKey(command.userId, command.idempotencyKey)
            ?.let { return resume(it, command) }

        val session = when (val read = cartClient.getCheckoutSession(command.checkoutSessionId, command.accessToken)) {
            is CartResult.Ok -> read.value
            is CartResult.Refused -> return refused(read.code, read.message)
            is CartResult.Unauthorized -> return unauthorized(read.code)
            CartResult.Unavailable -> return PlaceOrderResult.CartUnavailable
        }
        // The cart only answers for the caller's own cart; this guards against a mismatch anyway
        if (session.userId != command.userId) {
            return refused(CHECKOUT_SESSION_NOT_FOUND, "Checkout session not found")
        }

        val order = try {
            transactionTemplate.execute { orders.saveAndFlush(newOrder(session, command)) }!!
        } catch (ex: DataIntegrityViolationException) {
            // A concurrent request with the same key saved its order first
            val earlier = orders.findByUserIdAndIdempotencyKey(command.userId, command.idempotencyKey)
                ?: throw ex
            return resume(earlier, command)
        }
        logger.info("Saved order {} ({}) PENDING for checkout session {}", order.id, order.orderNumber, order.checkoutSessionId)
        return convertAndSubmit(order, command)
    }

    private fun resume(order: Order, command: PlaceOrderCommand): PlaceOrderResult {
        if (order.checkoutSessionId != command.checkoutSessionId) {
            return PlaceOrderResult.Refused(
                HttpStatus.UNPROCESSABLE_CONTENT,
                IDEMPOTENCY_KEY_REUSED,
                "This Idempotency-Key was already used for a different checkout session"
            )
        }
        return when (order.status) {
            OrderStatus.SUBMITTED -> PlaceOrderResult.Placed(order)
            OrderStatus.CANCELLED -> refused(order.cancelCode ?: CHECKOUT_SESSION_NOT_FOUND, "The order was not placed")
            OrderStatus.PENDING -> convertAndSubmit(order, command)
        }
    }

    private fun convertAndSubmit(order: Order, command: PlaceOrderCommand): PlaceOrderResult =
        when (val conversion = cartClient.convert(order.cartId, order.id, order.checkoutSessionId, command.accessToken)) {
            is CartResult.Ok -> submit(order)
            is CartResult.Refused -> cancel(order, conversion.code, command)
            is CartResult.Unauthorized -> unauthorized(conversion.code)
            CartResult.Unavailable -> {
                logger.warn("Cart unavailable converting cart {} for order {}; it stays PENDING", order.cartId, order.id)
                PlaceOrderResult.CartUnavailable
            }
        }

    private fun submit(order: Order): PlaceOrderResult {
        val submitted = transactionTemplate.execute {
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
        // A racing request may have cancelled it first; answer what the order actually is
        return if (submitted.status == OrderStatus.SUBMITTED) {
            PlaceOrderResult.Placed(submitted)
        } else {
            refused(submitted.cancelCode ?: CHECKOUT_SESSION_NOT_FOUND, "The order was not placed")
        }
    }

    private fun cancel(order: Order, code: String, command: PlaceOrderCommand): PlaceOrderResult {
        logger.info("Cart refused converting cart {} for order {} with {}; cancelling it", order.cartId, order.id, code)
        val current = transactionTemplate.execute {
            orders.markCancelled(order.id, code, Instant.now())
            orders.findById(order.id).get()
        }!!
        // A racing request may have submitted or cancelled it first; answer what the order actually is
        return resume(current, command)
    }

    private fun newOrder(session: CheckoutSession, command: PlaceOrderCommand): Order {
        val now = Instant.now()
        return Order(
            id = ids.generate(),
            orderNumber = orderNumbers.nextOrderNumber(LocalDate.ofInstant(now, ZoneOffset.UTC)).value,
            userId = command.userId,
            checkoutSessionId = session.checkoutSessionId,
            cartId = session.cartId,
            idempotencyKey = command.idempotencyKey,
            status = OrderStatus.PENDING,
            subtotal = session.summary.subtotal,
            currency = session.summary.currency,
            createdAt = now
        ).apply {
            session.items.forEach {
                addItem(
                    id = ids.generate(),
                    variantId = it.variantId,
                    quantity = it.quantity,
                    unitPrice = it.unitPrice,
                    lineTotal = it.lineTotal,
                    productSnapshot = objectMapper.writeValueAsString(it.productSnapshot)
                )
            }
        }
    }

    private fun refused(code: String, message: String) =
        PlaceOrderResult.Refused(if (code == CART_ALREADY_CONVERTED) HttpStatus.CONFLICT else HttpStatus.NOT_FOUND, code, message)

    private fun unauthorized(code: String) = PlaceOrderResult.Refused(HttpStatus.UNAUTHORIZED, code, code)

    companion object {
        const val CHECKOUT_SESSION_NOT_FOUND = "CHECKOUT_SESSION_NOT_FOUND"
        const val CART_ALREADY_CONVERTED = "CART_ALREADY_CONVERTED"
        const val IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED"
    }
}
