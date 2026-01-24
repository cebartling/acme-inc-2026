package com.acme.identity.infrastructure.security

import com.acme.identity.config.JwtConfig
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component

/**
 * Builder for creating secure authentication cookies.
 *
 * Creates HttpOnly, Secure, SameSite=Strict cookies for access and refresh tokens.
 * This configuration provides defense-in-depth security:
 *
 * - **HttpOnly**: Prevents JavaScript access (XSS protection)
 * - **Secure**: HTTPS-only transmission (MITM protection)
 * - **SameSite=Strict**: CSRF protection (blocks cross-site requests)
 * - **Path restriction**: Refresh token limited to specific endpoint
 *
 * Cookie configuration:
 * - access_token: Path=/, Max-Age=900s (15 minutes)
 * - refresh_token: Path=/api/v1/auth/refresh, Max-Age=604800s (7 days)
 *
 * @property config JWT configuration for expiry times.
 */
@Component
class AuthCookieBuilder(private val config: JwtConfig) {

    /**
     * Builds a secure cookie for the access token.
     *
     * The access token cookie is:
     * - Available on all paths (Path=/)
     * - Short-lived (15 minutes)
     * - HttpOnly, Secure, SameSite=Strict
     *
     * @param token The JWT access token string.
     * @return A configured [ResponseCookie] for the access token.
     */
    fun buildAccessTokenCookie(token: String): ResponseCookie {
        return ResponseCookie.from("access_token", token)
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/")
            .maxAge(config.accessTokenExpiry)
            .build()
    }

    /**
     * Builds a secure cookie for the refresh token.
     *
     * The refresh token cookie is:
     * - Restricted to the refresh endpoint (Path=/api/v1/auth/refresh)
     * - Long-lived (7 days)
     * - HttpOnly, Secure, SameSite=Strict
     *
     * Path restriction ensures the refresh token is only sent to the
     * token refresh endpoint, reducing exposure surface.
     *
     * @param token The JWT refresh token string.
     * @return A configured [ResponseCookie] for the refresh token.
     */
    fun buildRefreshTokenCookie(token: String): ResponseCookie {
        return ResponseCookie.from("refresh_token", token)
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/api/v1/auth/refresh")
            .maxAge(config.refreshTokenExpiry)
            .build()
    }

    /**
     * Builds cookies to clear both access and refresh tokens.
     *
     * Used during logout or session invalidation. Sets both cookies
     * with empty values and Max-Age=0 to immediately expire them.
     *
     * @return A list of [ResponseCookie] instances to clear auth cookies.
     */
    fun buildClearCookies(): List<ResponseCookie> {
        return listOf(
            ResponseCookie.from("access_token", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build(),
            ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth/refresh")
                .maxAge(0)
                .build()
        )
    }
}
