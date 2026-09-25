package com.acme.cart.api.v1

import com.acme.cart.application.AddItemToCartCommand
import com.acme.cart.application.AddItemToCartUseCase
import com.acme.cart.application.ExpireIdleGuestCartsUseCase
import com.acme.cart.application.MergeCartsCommand
import com.acme.cart.application.MergeCartsUseCase
import com.acme.cart.application.RemoveCartItemCommand
import com.acme.cart.application.RemoveCartItemUseCase
import com.acme.cart.application.UpdateCartItemQuantityCommand
import com.acme.cart.application.UpdateCartItemQuantityUseCase
import com.acme.cart.application.findActiveCart
import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartError
import com.acme.cart.domain.CartOwner
import com.acme.cart.domain.ProductSnapshot
import com.acme.cart.infrastructure.persistence.CartRepository
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
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
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/carts")
class CartController(
    private val addItemToCartUseCase: AddItemToCartUseCase,
    private val updateCartItemQuantityUseCase: UpdateCartItemQuantityUseCase,
    private val removeCartItemUseCase: RemoveCartItemUseCase,
    private val mergeCartsUseCase: MergeCartsUseCase,
    private val cartRepository: CartRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${acme.cart.cookie.secure}") private val secureCookie: Boolean,
    /** How long a guest session lasts without a cart request; idle carts expire after it (PIN-287). */
    @Value("\${acme.cart.guest-ttl}") private val guestTtl: Duration
) {

    /**
     * Adds an item to the caller's cart (US-0004-06).
     *
     * A signed-in caller's cart is their user cart (US-0004-08 AC-07). A guest's cart is
     * keyed by the `acme_session_id` cookie; a guest without a valid one gets a fresh
     * session ID, returned as an HttpOnly cookie on the response; an existing one slides.
     */
    @PostMapping("/items")
    fun addItem(
        @AuthenticationPrincipal jwt: Jwt?,
        @CookieValue(SESSION_COOKIE, required = false) sessionCookie: String?,
        @Valid @RequestBody request: AddToCartRequest,
        response: HttpServletResponse
    ): ResponseEntity<Any> {
        slideGuestSession(jwt, sessionCookie, response)
        val existingOwner = ownerOf(jwt, sessionCookie)
        val newSession = if (existingOwner == null) UUID.randomUUID().toString() else null
        val owner = existingOwner ?: CartOwner.Guest(newSession!!)

        val command = AddItemToCartCommand(
            owner = owner,
            variantId = request.variantId!!,
            quantity = request.quantity!!,
            productSnapshot = request.productSnapshot!!.toDomain(),
            startedNewSession = newSession != null
        )

        return addItemToCartUseCase.execute(command).fold(
            ifLeft = ::errorResponse,
            ifRight = { cart ->
                val response = ResponseEntity.status(HttpStatus.CREATED)
                if (newSession != null) {
                    response.header(HttpHeaders.SET_COOKIE, sessionCookie(newSession).toString())
                }
                response.body(toResponse(cart))
            }
        )
    }

    /**
     * The caller's cart (US-0004-07, AC-02/03/10): the user cart when signed in, else the
     * session's. 204 when there is no owner or no ACTIVE cart yet, so a first-time
     * visitor's page load is not an error.
     */
    @GetMapping("/current")
    fun getCurrent(
        @AuthenticationPrincipal jwt: Jwt?,
        @CookieValue(SESSION_COOKIE, required = false) sessionCookie: String?,
        response: HttpServletResponse
    ): ResponseEntity<Any> {
        slideGuestSession(jwt, sessionCookie, response)
        val cart = ownerOf(jwt, sessionCookie)?.let(cartRepository::findActiveCart)
            ?: return ResponseEntity.noContent().build()
        markGuestCartActive(cart)
        return ResponseEntity.ok(toResponse(cart))
    }

    /** Sets a line's quantity (AC-0004-07-04, AC-08). Only the caller's own cart is reachable. */
    @PatchMapping("/{cartId}/items/{itemId}")
    fun updateItemQuantity(
        @AuthenticationPrincipal jwt: Jwt?,
        @CookieValue(SESSION_COOKIE, required = false) sessionCookie: String?,
        @PathVariable cartId: UUID,
        @PathVariable itemId: UUID,
        @Valid @RequestBody request: UpdateQuantityRequest,
        response: HttpServletResponse
    ): ResponseEntity<Any> {
        slideGuestSession(jwt, sessionCookie, response)
        val owner = ownerOf(jwt, sessionCookie) ?: return errorResponse(CartError.CartItemNotFound(itemId))
        val command = UpdateCartItemQuantityCommand(owner, cartId, itemId, request.quantity!!)
        return updateCartItemQuantityUseCase.execute(command)
            .fold(ifLeft = ::errorResponse, ifRight = { ResponseEntity.ok(toResponse(it)) })
    }

    /** Removes a line (AC-0004-07-05). Only the caller's own cart is reachable. */
    @DeleteMapping("/{cartId}/items/{itemId}")
    fun removeItem(
        @AuthenticationPrincipal jwt: Jwt?,
        @CookieValue(SESSION_COOKIE, required = false) sessionCookie: String?,
        @PathVariable cartId: UUID,
        @PathVariable itemId: UUID,
        response: HttpServletResponse
    ): ResponseEntity<Any> {
        slideGuestSession(jwt, sessionCookie, response)
        val owner = ownerOf(jwt, sessionCookie) ?: return errorResponse(CartError.CartItemNotFound(itemId))
        return removeCartItemUseCase.execute(RemoveCartItemCommand(owner, cartId, itemId))
            .fold(ifLeft = ::errorResponse, ifRight = { ResponseEntity.ok(toResponse(it)) })
    }

    /**
     * Merges the caller's guest cart (from the session cookie) into their account cart
     * after sign-in (US-0004-08). Requires a signed-in caller. 204 when there was nothing
     * to merge and the user has no cart either.
     */
    @PostMapping("/merge")
    fun merge(
        @AuthenticationPrincipal jwt: Jwt?,
        @CookieValue(SESSION_COOKIE, required = false) sessionCookie: String?
    ): ResponseEntity<Any> {
        val customer = customerOf(jwt)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "SIGN_IN_REQUIRED"))

        return mergeCartsUseCase.execute(MergeCartsCommand(customer.userId, validSession(sessionCookie))).fold(
            ifLeft = ::errorResponse,
            ifRight = { outcome ->
                val cart = outcome.cart ?: return@fold ResponseEntity.noContent().build()
                ResponseEntity.ok(MergeResponse.from(toResponse(cart), outcome.result))
            }
        )
    }

    private fun toResponse(cart: Cart) =
        CartResponse.from(cart) { objectMapper.readValue(it, ProductSnapshot::class.java) }

    /**
     * `{"error": message, "code": code}`; a max-quantity error also carries `maxQuantity` so
     * the client can clamp. The code lets the client tell a gone line (reload quietly) from a
     * gone variant (the line is still there; show the message), which share a 404.
     */
    private fun errorResponse(error: CartError): ResponseEntity<Any> {
        val body = when (error) {
            is CartError.MaxQuantityExceeded ->
                mapOf("error" to error.message, "code" to codeFor(error), "maxQuantity" to error.maxQuantity)
            else -> mapOf("error" to error.message, "code" to codeFor(error))
        }
        return ResponseEntity.status(statusFor(error)).body(body)
    }

    private fun statusFor(error: CartError): HttpStatus = when (error) {
        is CartError.MaxQuantityExceeded -> HttpStatus.UNPROCESSABLE_CONTENT
        is CartError.VariantNotFound, is CartError.CartItemNotFound -> HttpStatus.NOT_FOUND
        is CartError.PricingUnavailable -> HttpStatus.SERVICE_UNAVAILABLE
    }

    private fun codeFor(error: CartError): String = when (error) {
        is CartError.MaxQuantityExceeded -> "MAX_QUANTITY_EXCEEDED"
        is CartError.VariantNotFound -> "VARIANT_NOT_FOUND"
        is CartError.CartItemNotFound -> "CART_ITEM_NOT_FOUND"
        is CartError.PricingUnavailable -> "PRICING_UNAVAILABLE"
    }

    private fun validSession(cookie: String?): String? = cookie?.takeIf(::isValidSessionId)

    /** A verified access token's subject is the identity service's user ID. */
    private fun customerOf(jwt: Jwt?): CartOwner.Customer? =
        jwt?.let { CartOwner.Customer(UUID.fromString(it.subject)) }

    /** Signed-in callers act on their user cart; everyone else on their session's. */
    private fun ownerOf(jwt: Jwt?, sessionCookie: String?): CartOwner? =
        customerOf(jwt) ?: validSession(sessionCookie)?.let(CartOwner::Guest)

    /**
     * Re-issues a guest's valid session cookie with a fresh [guestTtl] on every cart
     * request, so an active shopper's cart never expires under them (US-0004-12).
     */
    private fun slideGuestSession(jwt: Jwt?, sessionCookie: String?, response: HttpServletResponse) {
        if (jwt != null) return
        validSession(sessionCookie)?.let { response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(it).toString()) }
    }

    /**
     * A guest viewing their cart is using it, so it must not expire while the cookie is still
     * valid (PIN-287). Throttled to a write a day, because the header badge reads the cart on
     * every page: the loaded cart shows whether a refresh is due, so most views issue no UPDATE.
     * Changes already mark the cart active through the entity. A user cart never expires.
     */
    private fun markGuestCartActive(cart: Cart) {
        val sessionId = cart.sessionId ?: return
        val now = Instant.now()
        val staleBefore = now.minus(ExpireIdleGuestCartsUseCase.ACTIVITY_REFRESH_INTERVAL)
        if (cart.lastActiveAt < staleBefore) cartRepository.touchGuestCart(sessionId, now, staleBefore)
    }

    private fun sessionCookie(sessionId: String): ResponseCookie =
        ResponseCookie.from(SESSION_COOKIE, sessionId)
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite("Lax")
            .path("/")
            .maxAge(guestTtl)
            .build()

    /** Only IDs this service minted are accepted; anything else starts a new session. */
    private fun isValidSessionId(value: String): Boolean =
        runCatching { UUID.fromString(value) }.map { it.toString() == value }.getOrDefault(false)

    companion object {
        const val SESSION_COOKIE = "acme_session_id"
    }
}
