package com.acme.order.infrastructure.cart

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** What the cart answered, as the order service needs to act on it. */
sealed interface CartResult<out T> {
    data class Ok<T>(val value: T) : CartResult<T>

    /** A final answer: the order can't go ahead (e.g. `CHECKOUT_SESSION_NOT_FOUND`). */
    data class Refused(val code: String, val message: String) : CartResult<Nothing>

    /** The forwarded token was refused (`TOKEN_EXPIRED` or `INVALID_TOKEN`). */
    data class Unauthorized(val code: String) : CartResult<Nothing>

    /** No answer worth acting on (timeout, 5xx, `CART_CONFLICT`); the call can be retried. */
    data object Unavailable : CartResult<Nothing>
}

/** The cart's `CheckoutCartResponse` (PIN-365), the fields an order copies. */
data class CheckoutSession(
    val checkoutSessionId: UUID,
    val cartId: UUID,
    val userId: UUID?,
    val items: List<CheckoutItem>,
    val summary: CheckoutSummary
)

data class CheckoutItem(
    val variantId: UUID,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val lineTotal: BigDecimal,
    val productSnapshot: Map<String, Any?>
)

data class CheckoutSummary(val itemCount: Int, val subtotal: BigDecimal, val currency: String)

/** The cart's `CartConversionResponse` (PIN-331). */
data class CartConversion(val cartId: UUID, val orderId: UUID, val convertedAt: Instant)

private data class ConvertRequest(val orderId: UUID, val checkoutSessionId: UUID)

private data class CartErrorBody(val error: String? = null, val code: String? = null)

/**
 * Calls the cart on the customer's behalf (ADR-0044): the cart only reads identity's
 * `access_token` cookie, so the customer's token is forwarded as that cookie.
 */
@Component
class CartClient(private val cartRestClient: RestClient) {

    private val logger = LoggerFactory.getLogger(CartClient::class.java)

    /** `GET /api/v1/carts/checkout-sessions/{id}`: the session's cart, if it's live and the caller's. */
    fun getCheckoutSession(checkoutSessionId: UUID, accessToken: String): CartResult<CheckoutSession> =
        call("read checkout session $checkoutSessionId") {
            cartRestClient.get()
                .uri("/api/v1/carts/checkout-sessions/{id}", checkoutSessionId)
                .header(HttpHeaders.COOKIE, cookie(accessToken))
                .retrieve()
                .body(CheckoutSession::class.java)
        }

    /** `POST /api/v1/carts/{cartId}/convert`: safe to repeat for the same order and session. */
    fun convert(cartId: UUID, orderId: UUID, checkoutSessionId: UUID, accessToken: String): CartResult<CartConversion> =
        call("convert cart $cartId for order $orderId") {
            cartRestClient.post()
                .uri("/api/v1/carts/{cartId}/convert", cartId)
                .header(HttpHeaders.COOKIE, cookie(accessToken))
                .body(ConvertRequest(orderId, checkoutSessionId))
                .retrieve()
                .body(CartConversion::class.java)
        }

    private fun <T> call(description: String, request: () -> T?): CartResult<T> =
        try {
            request()?.let { CartResult.Ok(it) } ?: run {
                logger.warn("Cart answered {} with no body", description)
                CartResult.Unavailable
            }
        } catch (ex: HttpClientErrorException) {
            clientError(description, ex)
        } catch (ex: RestClientException) {
            logger.warn("Cart call failed: {}", description, ex)
            CartResult.Unavailable
        }

    private fun clientError(description: String, ex: HttpClientErrorException): CartResult<Nothing> {
        val body = try {
            ex.getResponseBodyAs(CartErrorBody::class.java)
        } catch (parseFailure: RestClientException) {
            logger.warn("Cart answered {} with {} and an unreadable error body", description, ex.statusCode, parseFailure)
            null
        }
        return when {
            // The cart's 401 body is {"error": "TOKEN_EXPIRED" | "INVALID_TOKEN"}, with no code
            ex.statusCode == HttpStatus.UNAUTHORIZED -> CartResult.Unauthorized(body?.error ?: "INVALID_TOKEN")
            body?.code in FINAL_CODES -> CartResult.Refused(body!!.code!!, body.error ?: body.code!!)
            else -> {
                logger.warn("Cart answered {} with {} {}", description, ex.statusCode, body?.code, ex)
                CartResult.Unavailable
            }
        }
    }

    private fun cookie(accessToken: String) = "$ACCESS_TOKEN_COOKIE=$accessToken"

    companion object {
        const val ACCESS_TOKEN_COOKIE = "access_token"

        /** The cart's answers that mean the order can't go ahead; anything else is retried. */
        val FINAL_CODES = setOf("CHECKOUT_SESSION_NOT_FOUND", "CART_NOT_FOUND", "CART_ALREADY_CONVERTED")
    }
}
