package com.acme.identity.infrastructure.security

import com.acme.identity.config.JwtConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

class AuthCookieBuilderTest {

    private lateinit var jwtConfig: JwtConfig
    private lateinit var authCookieBuilder: AuthCookieBuilder

    @BeforeEach
    fun setUp() {
        jwtConfig = JwtConfig(
            issuer = "https://auth.acme.com",
            audience = "https://api.acme.com",
            accessTokenExpiryMinutes = 15,
            refreshTokenExpiryDays = 7,
            keyRotationPeriodDays = 30
        )
        authCookieBuilder = AuthCookieBuilder(jwtConfig)
    }

    @Test
    fun `buildAccessTokenCookie should create cookie with correct attributes`() {
        val token = "test.access.token"
        val cookie = authCookieBuilder.buildAccessTokenCookie(token)

        assertEquals("access_token", cookie.name)
        assertEquals(token, cookie.value)
        assertTrue(cookie.isHttpOnly)
        assertTrue(cookie.isSecure)
        assertEquals("Strict", cookie.sameSite)
        assertEquals("/", cookie.path)
        assertEquals(900L, cookie.maxAge.seconds) // 15 minutes
    }

    @Test
    fun `buildRefreshTokenCookie should create cookie with correct attributes`() {
        val token = "test.refresh.token"
        val cookie = authCookieBuilder.buildRefreshTokenCookie(token)

        assertEquals("refresh_token", cookie.name)
        assertEquals(token, cookie.value)
        assertTrue(cookie.isHttpOnly)
        assertTrue(cookie.isSecure)
        assertEquals("Strict", cookie.sameSite)
        assertEquals("/api/v1/auth/refresh", cookie.path)
        assertEquals(604800L, cookie.maxAge.seconds) // 7 days
    }

    @Test
    fun `buildClearCookies should create two cookies with zero max age`() {
        val cookies = authCookieBuilder.buildClearCookies()

        assertEquals(2, cookies.size)

        val accessCookie = cookies.find { it.name == "access_token" }
        assertNotNull(accessCookie)
        assertEquals("", accessCookie.value)
        assertEquals(0L, accessCookie.maxAge.seconds)
        assertTrue(accessCookie.isHttpOnly)

        val refreshCookie = cookies.find { it.name == "refresh_token" }
        assertNotNull(refreshCookie)
        assertEquals("", refreshCookie.value)
        assertEquals(0L, refreshCookie.maxAge.seconds)
        assertTrue(refreshCookie.isHttpOnly)
    }

    @Test
    fun `access token cookie should have root path`() {
        val cookie = authCookieBuilder.buildAccessTokenCookie("token")

        assertEquals("/", cookie.path)
    }

    @Test
    fun `refresh token cookie should have restricted path`() {
        val cookie = authCookieBuilder.buildRefreshTokenCookie("token")

        assertEquals("/api/v1/auth/refresh", cookie.path)
    }

    @Test
    fun `cookies should have secure flag set`() {
        val accessCookie = authCookieBuilder.buildAccessTokenCookie("token")
        val refreshCookie = authCookieBuilder.buildRefreshTokenCookie("token")

        assertTrue(accessCookie.isSecure)
        assertTrue(refreshCookie.isSecure)
    }

    @Test
    fun `cookies should have httpOnly flag set`() {
        val accessCookie = authCookieBuilder.buildAccessTokenCookie("token")
        val refreshCookie = authCookieBuilder.buildRefreshTokenCookie("token")

        assertTrue(accessCookie.isHttpOnly)
        assertTrue(refreshCookie.isHttpOnly)
    }

    @Test
    fun `cookies should have SameSite strict`() {
        val accessCookie = authCookieBuilder.buildAccessTokenCookie("token")
        val refreshCookie = authCookieBuilder.buildRefreshTokenCookie("token")

        assertEquals("Strict", accessCookie.sameSite)
        assertEquals("Strict", refreshCookie.sameSite)
    }
}
