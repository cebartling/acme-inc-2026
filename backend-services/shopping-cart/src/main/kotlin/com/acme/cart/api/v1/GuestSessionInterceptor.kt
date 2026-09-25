package com.acme.cart.api.v1

import com.acme.cart.application.ExpireIdleGuestCartsUseCase
import com.acme.cart.infrastructure.persistence.CartRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.time.Instant

/**
 * Keeps a guest's session alive on every cart request (PIN-288), before the handler runs,
 * so a request rejected as invalid (400) is covered too.
 *
 * The rule: extending the cookie and recording activity always happen together. The
 * cookie gets a fresh guest TTL (US-0004-12), and the cart's `last_active_at` is touched
 * (throttled to once a day in the query), so the idle-cart job can never expire a cart
 * whose cookie is still valid (PIN-287). Signed-in callers act on their user cart and are
 * left alone.
 */
@Component
class GuestSessionInterceptor(
    private val guestSessionCookies: GuestSessionCookies,
    private val cartRepository: CartRepository
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (request.method == HttpMethod.OPTIONS.name() || isSignedIn()) return true
        val sessionId = guestSessionCookies.validSessionId(sessionCookieOf(request)) ?: return true

        response.addHeader(HttpHeaders.SET_COOKIE, guestSessionCookies.cookieFor(sessionId).toString())
        val now = Instant.now()
        cartRepository.touchGuestCart(sessionId, now, now.minus(ExpireIdleGuestCartsUseCase.ACTIVITY_REFRESH_INTERVAL))
        return true
    }

    private fun isSignedIn(): Boolean = SecurityContextHolder.getContext().authentication is JwtAuthenticationToken

    private fun sessionCookieOf(request: HttpServletRequest): String? =
        request.cookies?.firstOrNull { it.name == CartController.SESSION_COOKIE }?.value
}
