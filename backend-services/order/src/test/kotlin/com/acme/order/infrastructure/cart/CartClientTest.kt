package com.acme.order.infrastructure.cart

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class CartClientTest {

    private val builder = RestClient.builder().baseUrl("http://cart")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = CartClient(builder.build(), "order-secret")
    private val sessionId = UUID.randomUUID()
    private val cartId = UUID.randomUUID()
    private val orderId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val variantId = UUID.randomUUID()
    private val sessionUrl = "http://cart/api/v1/carts/checkout-sessions/$sessionId"
    private val convertUrl = "http://cart/api/v1/carts/$cartId/convert"

    private fun errorBody(status: HttpStatus, body: String) =
        withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body)

    @Test
    fun `reads the checkout session, forwarding the token as the access_token cookie`() {
        server.expect(requestTo(sessionUrl))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.COOKIE, "access_token=the-token"))
            .andExpect(header(CartClient.SERVICE_TOKEN_HEADER, "order-secret"))
            .andRespond(
                withSuccess(
                    """
                    {"checkoutSessionId":"$sessionId","expiresAt":"2026-10-22T12:30:00Z","cartId":"$cartId",
                     "userId":"$userId",
                     "items":[{"id":"${UUID.randomUUID()}","variantId":"$variantId","quantity":2,
                               "unitPrice":69.99,"lineTotal":139.98,
                               "productSnapshot":{"productId":"p-1","name":"Mouse","sku":"M-1",
                                                  "variantName":"Black","imageUrl":null,"attributes":{}}}],
                     "summary":{"itemCount":2,"subtotal":139.98,"currency":"USD"}}
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON
                )
            )

        val result = client.getCheckoutSession(sessionId, "the-token")

        val session = (result as CartResult.Ok).value
        assertEquals(cartId, session.cartId)
        assertEquals(userId, session.userId)
        assertEquals(BigDecimal("139.98"), session.summary.subtotal)
        assertEquals(variantId, session.items.single().variantId)
        assertEquals("Mouse", session.items.single().productSnapshot["name"])
    }

    @Test
    fun `a session that isn't live is refused with the cart's code`() {
        server.expect(requestTo(sessionUrl)).andRespond(
            errorBody(HttpStatus.NOT_FOUND, """{"error":"Checkout session not found","code":"CHECKOUT_SESSION_NOT_FOUND"}""")
        )

        assertEquals(
            CartResult.Refused("CHECKOUT_SESSION_NOT_FOUND", "Checkout session not found"),
            client.getCheckoutSession(sessionId, "the-token")
        )
    }

    @Test
    fun `a refused token is unauthorized with the cart's code`() {
        server.expect(requestTo(sessionUrl)).andRespond(errorBody(HttpStatus.UNAUTHORIZED, """{"error":"TOKEN_EXPIRED"}"""))

        assertEquals(CartResult.Unauthorized("TOKEN_EXPIRED"), client.getCheckoutSession(sessionId, "the-token"))
    }

    @Test
    fun `a server error is unavailable`() {
        server.expect(requestTo(sessionUrl)).andRespond(withServerError())

        assertEquals(CartResult.Unavailable, client.getCheckoutSession(sessionId, "the-token"))
    }

    @Test
    fun `converts the cart for the order, forwarding the token`() {
        server.expect(requestTo(convertUrl))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.COOKIE, "access_token=the-token"))
            .andExpect(header(CartClient.SERVICE_TOKEN_HEADER, "order-secret"))
            .andExpect(content().json("""{"orderId":"$orderId","checkoutSessionId":"$sessionId"}"""))
            .andRespond(
                withSuccess(
                    """{"cartId":"$cartId","status":"CONVERTED","orderId":"$orderId","convertedAt":"2026-10-22T12:00:00Z"}""",
                    MediaType.APPLICATION_JSON
                )
            )

        val result = client.convert(cartId, orderId, sessionId, "the-token")

        assertEquals(CartResult.Ok(CartConversion(cartId, orderId, Instant.parse("2026-10-22T12:00:00Z"))), result)
    }

    @Test
    fun `a cart already converted for another order is refused`() {
        server.expect(requestTo(convertUrl)).andRespond(
            errorBody(HttpStatus.CONFLICT, """{"error":"Cart already converted","code":"CART_ALREADY_CONVERTED"}""")
        )

        assertEquals(
            CartResult.Refused("CART_ALREADY_CONVERTED", "Cart already converted"),
            client.convert(cartId, orderId, sessionId, "the-token")
        )
    }

    @Test
    fun `a concurrent change to the cart is unavailable, so the convert can be retried`() {
        server.expect(requestTo(convertUrl)).andRespond(
            errorBody(HttpStatus.CONFLICT, """{"error":"Cart changed","code":"CART_CONFLICT"}""")
        )

        assertEquals(CartResult.Unavailable, client.convert(cartId, orderId, sessionId, "the-token"))
    }

    @Test
    fun `a 404 that isn't the cart's JSON error is unavailable, not refused`() {
        server.expect(requestTo(convertUrl)).andRespond(
            withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_HTML).body("<html>not found</html>")
        )

        assertEquals(CartResult.Unavailable, client.convert(cartId, orderId, sessionId, "the-token"))
    }

    @Test
    fun `a blank or padded service token is rejected at startup`() {
        listOf("", "  ", " order-secret", "order-secret\n").forEach { token ->
            assertThrows<IllegalArgumentException> { CartClient(builder.build(), token) }
        }
    }
}
