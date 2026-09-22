package com.acme.cart.api.v1

import com.acme.cart.application.AddItemToCartCommand
import com.acme.cart.application.AddItemToCartUseCase
import com.acme.cart.application.RemoveCartItemCommand
import com.acme.cart.application.RemoveCartItemUseCase
import com.acme.cart.application.UpdateCartItemQuantityCommand
import com.acme.cart.application.UpdateCartItemQuantityUseCase
import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartError
import com.acme.cart.domain.ProductSnapshot
import com.acme.cart.infrastructure.persistence.CartRepository
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
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
    private val updateCartItemQuantityUseCase: UpdateCartItemQuantityUseCase,
    private val removeCartItemUseCase: RemoveCartItemUseCase,
    private val cartRepository: CartRepository,
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
        val existingSession = validSession(sessionCookie)
        val sessionId = existingSession ?: UUID.randomUUID().toString()

        val command = AddItemToCartCommand(
            sessionId = sessionId,
            variantId = request.variantId!!,
            quantity = request.quantity!!,
            productSnapshot = request.productSnapshot!!.toDomain()
        )

        return addItemToCartUseCase.execute(command).fold(
            ifLeft = ::errorResponse,
            ifRight = { cart ->
                val response = ResponseEntity.status(HttpStatus.CREATED)
                if (existingSession == null) {
                    response.header(HttpHeaders.SET_COOKIE, sessionCookie(sessionId).toString())
                }
                response.body(toResponse(cart))
            }
        )
    }

    /**
     * The caller's cart (US-0004-07, AC-02/03/10). 204 when there is no session or no cart
     * yet, so a first-time visitor's page load is not an error.
     */
    @GetMapping("/current")
    fun getCurrent(
        @CookieValue(SESSION_COOKIE, required = false) sessionCookie: String?
    ): ResponseEntity<Any> {
        val cart = validSession(sessionCookie)?.let(cartRepository::findBySessionId)
            ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(toResponse(cart))
    }

    /** Sets a line's quantity (AC-0004-07-04, AC-08). Only the caller's own cart is reachable. */
    @PatchMapping("/{cartId}/items/{itemId}")
    fun updateItemQuantity(
        @CookieValue(SESSION_COOKIE, required = false) sessionCookie: String?,
        @PathVariable cartId: UUID,
        @PathVariable itemId: UUID,
        @Valid @RequestBody request: UpdateQuantityRequest
    ): ResponseEntity<Any> {
        val sessionId = validSession(sessionCookie) ?: return errorResponse(CartError.CartItemNotFound(itemId))
        val command = UpdateCartItemQuantityCommand(sessionId, cartId, itemId, request.quantity!!)
        return updateCartItemQuantityUseCase.execute(command)
            .fold(ifLeft = ::errorResponse, ifRight = { ResponseEntity.ok(toResponse(it)) })
    }

    /** Removes a line (AC-0004-07-05). Only the caller's own cart is reachable. */
    @DeleteMapping("/{cartId}/items/{itemId}")
    fun removeItem(
        @CookieValue(SESSION_COOKIE, required = false) sessionCookie: String?,
        @PathVariable cartId: UUID,
        @PathVariable itemId: UUID
    ): ResponseEntity<Any> {
        val sessionId = validSession(sessionCookie) ?: return errorResponse(CartError.CartItemNotFound(itemId))
        return removeCartItemUseCase.execute(RemoveCartItemCommand(sessionId, cartId, itemId))
            .fold(ifLeft = ::errorResponse, ifRight = { ResponseEntity.ok(toResponse(it)) })
    }

    private fun toResponse(cart: Cart) =
        CartResponse.from(cart) { objectMapper.readValue(it, ProductSnapshot::class.java) }

    /** `{"error": message}`; a max-quantity error also carries `maxQuantity` so the client can clamp. */
    private fun errorResponse(error: CartError): ResponseEntity<Any> {
        val body = when (error) {
            is CartError.MaxQuantityExceeded -> mapOf("error" to error.message, "maxQuantity" to error.maxQuantity)
            else -> mapOf("error" to error.message)
        }
        return ResponseEntity.status(statusFor(error)).body(body)
    }

    private fun statusFor(error: CartError): HttpStatus = when (error) {
        is CartError.MaxQuantityExceeded -> HttpStatus.UNPROCESSABLE_CONTENT
        is CartError.VariantNotFound, is CartError.CartItemNotFound -> HttpStatus.NOT_FOUND
        is CartError.PricingUnavailable -> HttpStatus.SERVICE_UNAVAILABLE
    }

    private fun validSession(cookie: String?): String? = cookie?.takeIf(::isValidSessionId)

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
