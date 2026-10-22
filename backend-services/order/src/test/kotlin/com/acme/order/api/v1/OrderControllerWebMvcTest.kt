package com.acme.order.api.v1

import com.acme.order.application.PlaceOrderCommand
import com.acme.order.application.PlaceOrderResult
import com.acme.order.application.PlaceOrderUseCase
import com.acme.order.config.SecurityConfig
import com.acme.order.domain.Order
import com.acme.order.domain.OrderStatus
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
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidationException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@WebMvcTest(OrderController::class)
@Import(SecurityConfig::class)
class OrderControllerWebMvcTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val useCase: PlaceOrderUseCase,
    @Autowired private val jwtDecoder: JwtDecoder
) {

    @TestConfiguration
    class Beans {
        @Bean
        fun placeOrderUseCase(): PlaceOrderUseCase = mockk()

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
        clearMocks(useCase, jwtDecoder)
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
}
