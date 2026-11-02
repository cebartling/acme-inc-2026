package com.acme.order.application

import com.acme.order.config.JacksonConfig
import com.acme.order.domain.Order
import com.acme.order.domain.OrderNumber
import com.acme.order.domain.OrderStatus
import com.acme.order.domain.events.OrderCreated
import com.acme.order.infrastructure.cart.CartClient
import com.acme.order.infrastructure.cart.CartConversion
import com.acme.order.infrastructure.cart.CartResult
import com.acme.order.infrastructure.cart.CheckoutItem
import com.acme.order.infrastructure.cart.CheckoutSession
import com.acme.order.infrastructure.cart.CheckoutSummary
import com.acme.order.infrastructure.messaging.OutboxWriter
import com.acme.order.infrastructure.persistence.OrderIdGenerator
import com.acme.order.infrastructure.persistence.OrderNumberSequenceRepository
import com.acme.order.infrastructure.persistence.OrderRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

class PlaceOrderUseCaseTest {

    private val orders = mockk<OrderRepository>()
    private val orderNumbers = mockk<OrderNumberSequenceRepository>()
    private val cartClient = mockk<CartClient>()
    private val outboxWriter = mockk<OutboxWriter>()
    private val transactionTemplate = TransactionTemplate(mockk<PlatformTransactionManager>(relaxed = true))
    private val useCase = PlaceOrderUseCase(
        orders, orderNumbers, OrderIdGenerator(), cartClient, outboxWriter, transactionTemplate, JacksonConfig().objectMapper()
    )

    private val userId = UUID.randomUUID()
    private val sessionId = UUID.randomUUID()
    private val cartId = UUID.randomUUID()
    private val command = PlaceOrderCommand(userId, "the-token", sessionId, "key-1")

    private val session = CheckoutSession(
        checkoutSessionId = sessionId,
        cartId = cartId,
        userId = userId,
        items = listOf(
            CheckoutItem(UUID.randomUUID(), 2, BigDecimal("69.99"), BigDecimal("139.98"), mapOf("name" to "Mouse")),
            CheckoutItem(UUID.randomUUID(), 1, BigDecimal("20.00"), BigDecimal("20.00"), mapOf("name" to "Pad"))
        ),
        summary = CheckoutSummary(itemCount = 3, subtotal = BigDecimal("159.98"), currency = "USD")
    )

    /** Saved orders, by ID, as the repository would return them. */
    private val stored = mutableMapOf<UUID, Order>()
    private val event = slot<OrderCreated>()

    init {
        every { orderNumbers.nextOrderNumber(any()) } returns OrderNumber.create(LocalDate.of(2026, 10, 22), 7)
        every { orders.findByUserIdAndIdempotencyKey(userId, "key-1") } answers {
            stored.values.firstOrNull { it.idempotencyKey == "key-1" }
        }
        every { orders.saveAndFlush(any<Order>()) } answers { firstArg<Order>().also { stored[it.id] = it } }
        every { orders.findById(any()) } answers { Optional.ofNullable(stored[firstArg()]) }
        every { orders.markSubmitted(any(), any()) } answers { transition(firstArg(), OrderStatus.SUBMITTED, null) }
        every { orders.markCancelled(any(), any(), any()) } answers { transition(firstArg(), OrderStatus.CANCELLED, secondArg()) }
        every { outboxWriter.write(capture(event)) } just runs
    }

    /** What the conditional update does: only a PENDING order moves. */
    private fun transition(id: UUID, to: OrderStatus, cancelCode: String?): Int {
        val current = stored.getValue(id)
        if (current.status != OrderStatus.PENDING) return 0
        stored[id] = copy(current, to, cancelCode)
        return 1
    }

    private fun copy(order: Order, status: OrderStatus, cancelCode: String? = order.cancelCode) = Order(
        order.id, order.orderNumber, order.userId, order.checkoutSessionId, order.cartId, order.idempotencyKey,
        status, order.subtotal, order.currency, cancelCode, order.createdAt
    ).also { copied -> order.items.forEach { copied.addItem(it.id, it.variantId, it.quantity, it.unitPrice, it.lineTotal, it.productSnapshot) } }

    private fun storedOrder(status: OrderStatus, checkoutSessionId: UUID = sessionId, cancelCode: String? = null) = Order(
        UUID.randomUUID(), "ACME-20261022-000001", userId, checkoutSessionId, cartId, "key-1",
        status, BigDecimal("159.98"), "USD", cancelCode, Instant.parse("2026-10-22T12:00:00Z")
    ).also { stored[it.id] = it }

    private fun sessionReads(result: CartResult<CheckoutSession>) {
        every { cartClient.getCheckoutSession(sessionId, "the-token") } returns result
    }

    private fun convertAnswers(result: CartResult<CartConversion>) {
        every { cartClient.convert(cartId, any(), sessionId, "the-token") } returns result
    }

    private fun converted() = CartResult.Ok(CartConversion(cartId, UUID.randomUUID(), Instant.now()))

    @Test
    fun `places the order from the session's cart, converts the cart and writes OrderCreated`() {
        sessionReads(CartResult.Ok(session))
        convertAnswers(converted())

        val result = useCase.execute(command)

        val order = (result as PlaceOrderResult.Placed).order
        assertEquals(OrderStatus.SUBMITTED, order.status)
        assertEquals("ACME-20261022-000007", order.orderNumber)
        assertEquals(BigDecimal("159.98"), order.subtotal)
        assertEquals(2, order.items.size)
        assertEquals(7, order.id.version(), "order IDs are UUID v7")
        assertTrue(order.items.first().productSnapshot.contains("Mouse"))
        verify { cartClient.convert(cartId, order.id, sessionId, "the-token") }

        val payload = event.captured.payload
        assertEquals(order.id, payload.orderId)
        assertEquals("ACME-20261022-000007", payload.orderNumber)
        assertEquals(userId, payload.userId)
        assertEquals("SUBMITTED", payload.status)
        assertEquals(BigDecimal("159.98"), payload.total.amount)
        assertEquals(3, payload.itemCount)
        assertEquals(sessionId, event.captured.correlationId)
    }

    @Test
    fun `a session that isn't live refuses the order and saves nothing`() {
        sessionReads(CartResult.Refused("CHECKOUT_SESSION_NOT_FOUND", "Checkout session not found"))

        val result = useCase.execute(command)

        assertEquals(
            PlaceOrderResult.Refused(HttpStatus.NOT_FOUND, "CHECKOUT_SESSION_NOT_FOUND", "Checkout session not found"),
            result
        )
        verify(exactly = 0) { orders.saveAndFlush(any<Order>()) }
    }

    @Test
    fun `a session for another customer is refused`() {
        sessionReads(CartResult.Ok(session.copy(userId = UUID.randomUUID())))

        val result = useCase.execute(command)

        assertEquals("CHECKOUT_SESSION_NOT_FOUND", (result as PlaceOrderResult.Refused).code)
        verify(exactly = 0) { orders.saveAndFlush(any<Order>()) }
    }

    @Test
    fun `a token the cart refuses is a 401 with its code`() {
        sessionReads(CartResult.Unauthorized("TOKEN_EXPIRED"))

        val result = useCase.execute(command) as PlaceOrderResult.Refused

        assertEquals(HttpStatus.UNAUTHORIZED, result.status)
        assertEquals("TOKEN_EXPIRED", result.code)
    }

    @Test
    fun `a cart that refuses the conversion cancels the order without announcing it`() {
        sessionReads(CartResult.Ok(session))
        convertAnswers(CartResult.Refused("CART_ALREADY_CONVERTED", "Cart already converted"))

        val result = useCase.execute(command)

        assertEquals(PlaceOrderResult.Refused(HttpStatus.CONFLICT, "CART_ALREADY_CONVERTED", "The order was not placed"), result)
        val saved = stored.values.single()
        assertEquals(OrderStatus.CANCELLED, saved.status)
        assertEquals("CART_ALREADY_CONVERTED", saved.cancelCode)
        verify(exactly = 0) { outboxWriter.write(any()) }
    }

    @Test
    fun `an unavailable cart leaves the order PENDING, and the same key then completes it`() {
        sessionReads(CartResult.Ok(session))
        convertAnswers(CartResult.Unavailable)

        assertEquals(PlaceOrderResult.CartUnavailable, useCase.execute(command))
        assertEquals(OrderStatus.PENDING, stored.values.single().status)
        verify(exactly = 0) { outboxWriter.write(any()) }

        convertAnswers(converted())
        val retried = useCase.execute(command)

        assertEquals(OrderStatus.SUBMITTED, (retried as PlaceOrderResult.Placed).order.status)
        assertEquals(1, stored.size)
        verify(exactly = 1) { cartClient.getCheckoutSession(any(), any()) }
        verify(exactly = 1) { outboxWriter.write(any()) }
    }

    @Test
    fun `a repeated key for a submitted order answers it again without calling the cart`() {
        val earlier = storedOrder(OrderStatus.SUBMITTED)

        val result = useCase.execute(command)

        assertEquals(earlier.id, (result as PlaceOrderResult.Placed).order.id)
        verify(exactly = 0) { cartClient.getCheckoutSession(any(), any()) }
        verify(exactly = 0) { cartClient.convert(any(), any(), any(), any()) }
    }

    @Test
    fun `a repeated key for a cancelled order is refused again with its code`() {
        storedOrder(OrderStatus.CANCELLED, cancelCode = "CHECKOUT_SESSION_NOT_FOUND")

        val result = useCase.execute(command) as PlaceOrderResult.Refused

        assertEquals(HttpStatus.NOT_FOUND, result.status)
        assertEquals("CHECKOUT_SESSION_NOT_FOUND", result.code)
        verify(exactly = 0) { cartClient.convert(any(), any(), any(), any()) }
    }

    @Test
    fun `a repeated key for an order the customer cancelled, which has no code, is refused`() {
        storedOrder(OrderStatus.CANCELLED, cancelCode = null)

        val result = useCase.execute(command) as PlaceOrderResult.Refused

        assertEquals(HttpStatus.NOT_FOUND, result.status)
        assertEquals("CHECKOUT_SESSION_NOT_FOUND", result.code)
        verify(exactly = 0) { cartClient.convert(any(), any(), any(), any()) }
    }

    @Test
    fun `a repeated key with a different checkout session is a 422`() {
        storedOrder(OrderStatus.SUBMITTED, checkoutSessionId = UUID.randomUUID())

        val result = useCase.execute(command) as PlaceOrderResult.Refused

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, result.status)
        assertEquals("IDEMPOTENCY_KEY_REUSED", result.code)
    }

    @Test
    fun `losing a race to save with the same key resumes the order that won`() {
        sessionReads(CartResult.Ok(session))
        val winner = Order(
            UUID.randomUUID(), "ACME-20261022-000001", userId, sessionId, cartId, "key-1",
            OrderStatus.PENDING, BigDecimal("159.98"), "USD", null, Instant.now()
        )
        // The first lookup finds nothing; by the time the insert runs, the winner has saved
        every { orders.findByUserIdAndIdempotencyKey(userId, "key-1") } returnsMany listOf(null, winner)
        every { orders.saveAndFlush(any<Order>()) } answers {
            stored[winner.id] = winner
            throw DataIntegrityViolationException("uq_orders_user_idempotency_key")
        }
        every { cartClient.convert(cartId, winner.id, sessionId, "the-token") } returns converted()

        val result = useCase.execute(command)

        assertEquals(winner.id, (result as PlaceOrderResult.Placed).order.id)
        assertEquals(OrderStatus.SUBMITTED, stored.getValue(winner.id).status)
    }

    @Test
    fun `when another request already submitted the order, no second OrderCreated is written`() {
        val pending = storedOrder(OrderStatus.PENDING)
        every { cartClient.convert(cartId, pending.id, sessionId, "the-token") } answers {
            // A racing request with the same key finishes first
            stored[pending.id] = copy(pending, OrderStatus.SUBMITTED)
            converted()
        }

        val result = useCase.execute(command)

        assertEquals(OrderStatus.SUBMITTED, (result as PlaceOrderResult.Placed).order.status)
        verify(exactly = 0) { outboxWriter.write(any()) }
    }

    @Test
    fun `a retry after the abandoned order job is refused with its code`() {
        storedOrder(OrderStatus.CANCELLED, cancelCode = CancelAbandonedOrdersUseCase.PLACEMENT_ABANDONED)

        val result = useCase.execute(command) as PlaceOrderResult.Refused

        assertEquals(HttpStatus.NOT_FOUND, result.status)
        assertEquals("PLACEMENT_ABANDONED", result.code)
        verify(exactly = 0) { cartClient.convert(any(), any(), any(), any()) }
    }

    @Test
    fun `when the abandoned order job cancels the order during a retry, it stays cancelled`() {
        val pending = storedOrder(OrderStatus.PENDING)
        every { cartClient.convert(cartId, pending.id, sessionId, "the-token") } answers {
            stored[pending.id] = copy(pending, OrderStatus.CANCELLED, CancelAbandonedOrdersUseCase.PLACEMENT_ABANDONED)
            converted()
        }

        val result = useCase.execute(command) as PlaceOrderResult.Refused

        assertEquals("PLACEMENT_ABANDONED", result.code)
        assertEquals(OrderStatus.CANCELLED, stored.getValue(pending.id).status)
        verify(exactly = 0) { outboxWriter.write(any()) }
    }
}
