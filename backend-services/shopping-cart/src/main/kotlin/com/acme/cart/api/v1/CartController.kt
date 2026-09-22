package com.acme.cart.api.v1

import com.acme.cart.application.AddItemToCartCommand
import com.acme.cart.application.AddItemToCartUseCase
import com.acme.cart.domain.CartError
import com.acme.cart.domain.ProductSnapshot
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.util.UUID

@RestController
@RequestMapping("/api/v1/carts")
class CartController(
    private val addItemToCartUseCase: AddItemToCartUseCase,
    private val objectMapper: ObjectMapper,
    @Value("\${acme.cart.cookie.secure}") private val secureCookie: Boolean
) {

    /**
     * Adds an item to the caller's cart (US-0004-06).
     *
     * The cart is keyed by the `acme_session_id` cookie. A request without a valid one
     * gets a fresh session ID, returned as an HttpOnly cookie on the response.
     */
    @PostMapping("/items")
    fun addItem(
        @CookieValue(SESSION_COOKIE, required = false) sessionCookie: String?,
        @Valid @RequestBody request: AddToCartRequest
    ): ResponseEntity<Any> {
        val existingSession = sessionCookie?.takeIf(::isValidSessionId)
        val sessionId = existingSession ?: UUID.randomUUID().toString()

        val command = AddItemToCartCommand(
            sessionId = sessionId,
            variantId = request.variantId!!,
            quantity = request.quantity!!,
            productSnapshot = request.productSnapshot!!.toDomain()
        )

        return addItemToCartUseCase.execute(command).fold(
            ifLeft = { error -> ResponseEntity.status(statusFor(error)).body(mapOf("error" to error.message)) },
            ifRight = { cart ->
                val response = ResponseEntity.status(HttpStatus.CREATED)
                if (existingSession == null) {
                    response.header(HttpHeaders.SET_COOKIE, sessionCookie(sessionId).toString())
                }
                response.body(CartResponse.from(cart) { objectMapper.readValue(it, ProductSnapshot::class.java) })
            }
        )
    }

    private fun statusFor(error: CartError): HttpStatus = when (error) {
        is CartError.MaxQuantityExceeded -> HttpStatus.UNPROCESSABLE_CONTENT
        is CartError.VariantNotFound -> HttpStatus.NOT_FOUND
        is CartError.PricingUnavailable -> HttpStatus.SERVICE_UNAVAILABLE
    }

    private fun sessionCookie(sessionId: String): ResponseCookie =
        ResponseCookie.from(SESSION_COOKIE, sessionId)
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite("Lax")
            .path("/")
            .maxAge(SESSION_TTL)
            .build()

    /** Only IDs this service minted are accepted; anything else starts a new session. */
    private fun isValidSessionId(value: String): Boolean =
        runCatching { UUID.fromString(value) }.map { it.toString() == value }.getOrDefault(false)

    companion object {
        const val SESSION_COOKIE = "acme_session_id"
        val SESSION_TTL: Duration = Duration.ofDays(30)
    }
}
