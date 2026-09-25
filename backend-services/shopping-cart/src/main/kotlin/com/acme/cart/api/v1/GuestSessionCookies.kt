package com.acme.cart.api.v1

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

/**
 * The guest session cookie, `acme_session_id` (US-0004-07): which values are accepted and
 * how the cookie is built. The one place that sets it, whether it is newly minted by
 * [CartController.addItem] or re-issued by [GuestSessionInterceptor].
 */
@Component
class GuestSessionCookies(
    @Value("\${acme.cart.cookie.secure}") private val secure: Boolean,
    /** How long a guest session lasts without a cart request; idle carts expire after it (PIN-287). */
    @Value("\${acme.cart.guest-ttl}") private val guestTtl: Duration
) {

    /** Only IDs this service minted are accepted; anything else starts a new session. */
    fun validSessionId(value: String?): String? = value?.takeIf(::isMintedId)

    /** HttpOnly, SameSite=Lax, and valid for a full [guestTtl] from now. */
    fun cookieFor(sessionId: String): ResponseCookie =
        ResponseCookie.from(CartController.SESSION_COOKIE, sessionId)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(guestTtl)
            .build()

    private fun isMintedId(value: String): Boolean =
        runCatching { UUID.fromString(value) }.map { it.toString() == value }.getOrDefault(false)
}
