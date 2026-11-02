package com.acme.order.api.v1

import com.acme.order.application.CancelOrderUseCase
import com.acme.order.application.FindOrderByKeyUseCase
import com.acme.order.application.GetOrderResult
import com.acme.order.application.GetOrderUseCase
import com.acme.order.application.ListOrdersUseCase
import com.acme.order.application.OrderDetails
import com.acme.order.application.OrderLine
import com.acme.order.application.PlaceOrderCommand
import com.acme.order.application.PlaceOrderResult
import com.acme.order.application.PlaceOrderUseCase
import com.acme.order.config.CorsConfig
import com.acme.order.config.SecurityConfig
import com.acme.order.domain.Order
import com.acme.order.domain.OrderStatus
import com.acme.order.infrastructure.persistence.OrderSummary
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidationException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.options
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@WebMvcTest(OrderController::class)
@Import(SecurityConfig::class, CorsConfig::class)
class OrderControllerWebMvcTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val useCase: PlaceOrderUseCase,
    @Autowired private val getOrder: GetOrderUseCase,
    @Autowired private val listOrders: ListOrdersUseCase,
    @Autowired private val findOrderByKey: FindOrderByKeyUseCase,
    @Autowired private val cancelOrder: CancelOrderUseCase,
    @Autowired private val jwtDecoder: JwtDecoder
) {

    @TestConfiguration
    class Beans {
        @Bean
        fun placeOrderUseCase(): PlaceOrderUseCase = mockk()

        @Bean
        fun getOrderUseCase(): GetOrderUseCase = mockk()

        @Bean
        fun listOrdersUseCase(): ListOrdersUseCase = mockk()

        @Bean
        fun findOrderByKeyUseCase(): FindOrderByKeyUseCase = mockk()

        @Bean
        fun cancelOrderUseCase(): CancelOrderUseCase = mockk()

        /** Stands in for the JWKS-backed decoder; tests decide what a token decodes to. */
        @Bean
        fun jwtDecoder(): JwtDecoder = mockk()
    }

    private val userId = UUID.randomUUID()
    private val sessionId = UUID.randomUUID()
    private val body = """{"checkoutSessionId":"$sessionId"}"""

    private val order = Order(
        id = UUID.randomUUID(),
        orderNumber = "ACME-20261022-000001",
        userId = userId,
        checkoutSessionId = sessionId,
        cartId = UUID.randomUUID(),
        idempotencyKey = "key-1",
        status = OrderStatus.SUBMITTED,
        subtotal = BigDecimal("139.98"),
        currency = "USD",
        createdAt = Instant.parse("2026-10-22T12:00:00Z")
    )

    @BeforeEach
    fun setUp() {
        clearMocks(useCase, getOrder, listOrders, findOrderByKey, cancelOrder, jwtDecoder)
        every { jwtDecoder.decode("good-token") } returns Jwt.withTokenValue("good-token")
            .header("alg", "RS256")
            .subject(userId.toString())
            .build()
    }

    private fun accessToken(value: String = "good-token") = Cookie(SecurityConfig.ACCESS_TOKEN_COOKIE, value)

    @Test
    fun `a placed order is a 201 with its number, status and subtotal`() {
        val command = slot<PlaceOrderCommand>()
        every { useCase.execute(capture(command)) } returns PlaceOrderResult.Placed(order)

        mockMvc.post("/api/v1/orders") {
            cookie(accessToken())
            header("Idempotency-Key", " key-1 ")
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isCreated() }
            jsonPath("$.orderId") { value(order.id.toString()) }
            jsonPath("$.orderNumber") { value("ACME-20261022-000001") }
            jsonPath("$.status") { value("SUBMITTED") }
            jsonPath("$.createdAt") { value("2026-10-22T12:00:00Z") }
            jsonPath("$.summary.subtotal.amount") { value(139.98) }
            jsonPath("$.summary.subtotal.currency") { value("USD") }
        }

        assertEquals(PlaceOrderCommand(userId, "good-token", sessionId, "key-1"), command.captured)
    }

    @Test
    fun `no access token is a 401 SIGN_IN_REQUIRED`() {
        mockMvc.post("/api/v1/orders") {
            header("Idempotency-Key", "key-1")
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("SIGN_IN_REQUIRED") }
        }
        verify(exactly = 0) { useCase.execute(any()) }
    }

    @Test
    fun `no access token is a 401 even when the body is invalid too`() {
        mockMvc.post("/api/v1/orders") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("SIGN_IN_REQUIRED") }
        }
    }

    @Test
    fun `an expired token is a 401 TOKEN_EXPIRED`() {
        every { jwtDecoder.decode("stale-token") } throws JwtValidationException(
            "An error occurred while attempting to decode the Jwt: Jwt expired at 2026-01-01T00:00:00Z",
            listOf(OAuth2Error("invalid_token", "Jwt expired at 2026-01-01T00:00:00Z", null))
        )

        mockMvc.post("/api/v1/orders") {
            cookie(accessToken("stale-token"))
            header("Idempotency-Key", "key-1")
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("TOKEN_EXPIRED") }
        }
    }

    @Test
    fun `a missing or blank Idempotency-Key is a 400 IDEMPOTENCY_KEY_REQUIRED`() {
        listOf(null, "   ").forEach { key ->
            mockMvc.post("/api/v1/orders") {
                cookie(accessToken())
                if (key != null) header("Idempotency-Key", key)
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("IDEMPOTENCY_KEY_REQUIRED") }
            }
        }
        verify(exactly = 0) { useCase.execute(any()) }
    }

    @Test
    fun `a body without a checkout session, or one that is no UUID, is a 400 INVALID_REQUEST`() {
        listOf("{}", """{"checkoutSessionId":"nope"}""").forEach { invalid ->
            mockMvc.post("/api/v1/orders") {
                cookie(accessToken())
                header("Idempotency-Key", "key-1")
                contentType = MediaType.APPLICATION_JSON
                content = invalid
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_REQUEST") }
            }
        }
    }

    @Test
    fun `a refusal is answered with its status and code`() {
        every { useCase.execute(any()) } returns
            PlaceOrderResult.Refused(HttpStatus.UNPROCESSABLE_CONTENT, "IDEMPOTENCY_KEY_REUSED", "Key reused")

        mockMvc.post("/api/v1/orders") {
            cookie(accessToken())
            header("Idempotency-Key", "key-1")
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isEqualTo(422) }
            jsonPath("$.error") { value("Key reused") }
            jsonPath("$.code") { value("IDEMPOTENCY_KEY_REUSED") }
        }
    }

    // --- PIN-367: reading orders ---------------------------------------------------------

    private val details = OrderDetails(
        id = order.id,
        orderNumber = order.orderNumber,
        status = OrderStatus.SUBMITTED,
        createdAt = order.createdAt,
        subtotal = BigDecimal("139.98"),
        currency = "USD",
        lines = listOf(
            OrderLine(
                UUID.randomUUID(), UUID.randomUUID(), "p-1", "Gadget Pro", "Black", "GP-BLK", null,
                2, BigDecimal("69.99"), BigDecimal("139.98")
            )
        )
    )

    @Test
    fun `an order is a 200 with its lines and subtotal`() {
        every { getOrder.execute(order.id, userId) } returns GetOrderResult.Found(details)

        mockMvc.get("/api/v1/orders/${order.id}") { cookie(accessToken()) }.andExpect {
            status { isOk() }
            jsonPath("$.orderId") { value(order.id.toString()) }
            jsonPath("$.orderNumber") { value("ACME-20261022-000001") }
            jsonPath("$.status") { value("SUBMITTED") }
            jsonPath("$.lineItems[0].productName") { value("Gadget Pro") }
            jsonPath("$.lineItems[0].quantity") { value(2) }
            jsonPath("$.lineItems[0].unitPrice.amount") { value(69.99) }
            jsonPath("$.lineItems[0].lineTotal.currency") { value("USD") }
            jsonPath("$.pricing.subtotal.amount") { value(139.98) }
        }
    }

    @Test
    fun `an order the caller can't see is a 404 ORDER_NOT_FOUND`() {
        every { getOrder.execute(any(), any()) } returns GetOrderResult.NotFound

        mockMvc.get("/api/v1/orders/${UUID.randomUUID()}") { cookie(accessToken()) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ORDER_NOT_FOUND") }
        }
    }

    @Test
    fun `an order ID that is no UUID is a 400 INVALID_REQUEST`() {
        mockMvc.get("/api/v1/orders/not-a-uuid") { cookie(accessToken()) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
            jsonPath("$.error") { value("Invalid request: orderId") }
        }
    }

    @Test
    fun `the order list is a page of summaries, with 1-based paging`() {
        val summary = OrderSummary(order.id, order.orderNumber, OrderStatus.SUBMITTED, order.createdAt, BigDecimal("139.98"), "USD", 2)
        every { listOrders.execute(userId, 2, 1) } returns PageImpl(listOf(summary), PageRequest.of(1, 1), 3)

        mockMvc.get("/api/v1/orders?page=2&pageSize=1") { cookie(accessToken()) }.andExpect {
            status { isOk() }
            jsonPath("$.orders[0].orderId") { value(order.id.toString()) }
            jsonPath("$.orders[0].itemCount") { value(2) }
            jsonPath("$.orders[0].subtotal.amount") { value(139.98) }
            jsonPath("$.page") { value(2) }
            jsonPath("$.pageSize") { value(1) }
            jsonPath("$.totalResults") { value(3) }
            jsonPath("$.totalPages") { value(3) }
        }
    }

    @Test
    fun `the order list defaults to the first page of 24`() {
        every { listOrders.execute(userId, 1, 24) } returns Page.empty()

        mockMvc.get("/api/v1/orders") { cookie(accessToken()) }.andExpect { status { isOk() } }
    }

    @Test
    fun `paging out of range is a 400 INVALID_REQUEST`() {
        listOf("page=0", "page=10001", "pageSize=0", "pageSize=101", "page=two").forEach { query ->
            mockMvc.get("/api/v1/orders?$query") { cookie(accessToken()) }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_REQUEST") }
            }
        }
        verify(exactly = 0) { listOrders.execute(any(), any(), any()) }
    }

    @Test
    fun `an order found by its key is a 200 with its number and status, and places nothing`() {
        every { findOrderByKey.execute(userId, "key-1") } returns order

        mockMvc.get("/api/v1/orders/by-idempotency-key") {
            cookie(accessToken())
            header("Idempotency-Key", " key-1 ")
        }.andExpect {
            status { isOk() }
            jsonPath("$.orderId") { value(order.id.toString()) }
            jsonPath("$.orderNumber") { value("ACME-20261022-000001") }
            jsonPath("$.status") { value("SUBMITTED") }
        }

        verify(exactly = 0) { useCase.execute(any()) }
    }

    @Test
    fun `a key with no order is a 404 ORDER_NOT_FOUND`() {
        every { findOrderByKey.execute(userId, "key-1") } returns null

        mockMvc.get("/api/v1/orders/by-idempotency-key") {
            cookie(accessToken())
            header("Idempotency-Key", "key-1")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ORDER_NOT_FOUND") }
        }
    }

    @Test
    fun `finding an order without a usable key is a 400 IDEMPOTENCY_KEY_REQUIRED`() {
        mockMvc.get("/api/v1/orders/by-idempotency-key") { cookie(accessToken()) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("IDEMPOTENCY_KEY_REQUIRED") }
        }
        mockMvc.get("/api/v1/orders/by-idempotency-key") {
            cookie(accessToken())
            header("Idempotency-Key", "k".repeat(256))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("IDEMPOTENCY_KEY_REQUIRED") }
        }
    }

    @Test
    fun `reading orders without signing in is a 401 SIGN_IN_REQUIRED`() {
        listOf(
            "/api/v1/orders",
            "/api/v1/orders/${order.id}",
            "/api/v1/orders/by-idempotency-key"
        ).forEach { path ->
            mockMvc.get(path).andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("SIGN_IN_REQUIRED") }
            }
        }
    }

    // --- PIN-429: cancelling an order -------------------------------------------------------

    private fun cancel(id: UUID = order.id, reason: String = """{"reason":"Changed my mind"}""", signedIn: Boolean = true) =
        mockMvc.post("/api/v1/orders/$id/cancel") {
            if (signedIn) cookie(accessToken())
            contentType = MediaType.APPLICATION_JSON
            content = reason
        }

    @Test
    fun `a cancelled order is a 200 in the shape of a read`() {
        every { cancelOrder.execute(order.id, userId, "Changed my mind") } returns
            GetOrderResult.Found(details.copy(status = OrderStatus.CANCELLED))

        cancel().andExpect {
            status { isOk() }
            jsonPath("$.orderId") { value(order.id.toString()) }
            jsonPath("$.status") { value("CANCELLED") }
            jsonPath("$.lineItems[0].productName") { value("Gadget Pro") }
        }
    }

    @Test
    fun `cancelling an order the caller can't see is a 404 ORDER_NOT_FOUND`() {
        every { cancelOrder.execute(any(), any(), any()) } returns GetOrderResult.NotFound

        cancel().andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ORDER_NOT_FOUND") }
        }
    }

    @Test
    fun `a reason of 500 characters is accepted`() {
        val longest = "r".repeat(500)
        every { cancelOrder.execute(order.id, userId, longest) } returns
            GetOrderResult.Found(details.copy(status = OrderStatus.CANCELLED))

        cancel(reason = """{"reason":"$longest"}""").andExpect { status { isOk() } }
    }

    @Test
    fun `a missing, blank or over-long reason is a 400 INVALID_REQUEST`() {
        listOf("{}", """{"reason":"   "}""", """{"reason":"${"r".repeat(501)}"}""", "").forEach { invalid ->
            cancel(reason = invalid).andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_REQUEST") }
            }
        }
        verify(exactly = 0) { cancelOrder.execute(any(), any(), any()) }
    }

    @Test
    fun `a body that isn't JSON is refused, as the CSRF reasoning needs`() {
        mockMvc.post("/api/v1/orders/${order.id}/cancel") {
            cookie(accessToken())
            contentType = MediaType.TEXT_PLAIN
            content = """{"reason":"Changed my mind"}"""
        }.andExpect { status { isUnsupportedMediaType() } }
        verify(exactly = 0) { cancelOrder.execute(any(), any(), any()) }
    }

    @Test
    fun `cancelling without signing in is a 401 SIGN_IN_REQUIRED`() {
        cancel(signedIn = false).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("SIGN_IN_REQUIRED") }
        }
    }

    @Test
    fun `an unavailable cart is a 503 CART_UNAVAILABLE`() {
        every { useCase.execute(any()) } returns PlaceOrderResult.CartUnavailable

        mockMvc.post("/api/v1/orders") {
            cookie(accessToken())
            header("Idempotency-Key", "key-1")
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("CART_UNAVAILABLE") }
        }
    }

    @Test
    fun `an allowed origin's preflight is answered with credentials allowed`() {
        mockMvc.options("/api/v1/orders") {
            header("Origin", "http://localhost:7600")
            header("Access-Control-Request-Method", "POST")
            header("Access-Control-Request-Headers", "Content-Type, Idempotency-Key")
        }.andExpect {
            status { isOk() }
            header { string("Access-Control-Allow-Origin", "http://localhost:7600") }
            header { string("Access-Control-Allow-Credentials", "true") }
        }
    }

    @Test
    fun `another origin's preflight is refused`() {
        mockMvc.options("/api/v1/orders") {
            header("Origin", "http://evil.example")
            header("Access-Control-Request-Method", "POST")
        }.andExpect {
            status { isForbidden() }
            header { doesNotExist("Access-Control-Allow-Origin") }
        }
    }
}
