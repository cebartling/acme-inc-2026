package com.acme.identity.application

import com.acme.identity.domain.Session
import com.acme.identity.domain.User
import com.acme.identity.domain.DeviceTrust
import com.acme.identity.domain.events.UserLoggedIn
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.EventStoreRepository
import com.acme.identity.infrastructure.persistence.UserRepository
import com.acme.identity.infrastructure.security.AuthCookieBuilder
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.*

class AuthenticationSessionServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var sessionService: SessionService
    private lateinit var tokenService: TokenService
    private lateinit var deviceTrustService: DeviceTrustService
    private lateinit var authCookieBuilder: AuthCookieBuilder
    private lateinit var eventStoreRepository: EventStoreRepository
    private lateinit var userEventPublisher: UserEventPublisher
    private lateinit var authenticationSessionService: AuthenticationSessionService

    private val testUserId = UUID.randomUUID()
    private val testSessionId = "sess_${UUID.randomUUID()}"
    private val testIpAddress = "192.168.1.100"
    private val testUserAgent = "Mozilla/5.0 Test Browser"
    private val testDeviceFingerprint = "fp_test_12345"
    private val testCorrelationId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        sessionService = mockk()
        tokenService = mockk()
        deviceTrustService = mockk()
        authCookieBuilder = mockk()
        eventStoreRepository = mockk(relaxed = true)
        userEventPublisher = mockk(relaxed = true)

        authenticationSessionService = AuthenticationSessionService(
            userRepository = userRepository,
            sessionService = sessionService,
            tokenService = tokenService,
            deviceTrustService = deviceTrustService,
            authCookieBuilder = authCookieBuilder,
            eventStoreRepository = eventStoreRepository,
            userEventPublisher = userEventPublisher
        )
    }

    @Test
    fun `createAuthenticatedSession should create session, tokens, and cookies with all parameters`() {
        // Given
        val testUser = createTestUser()
        val testSession = createTestSession()
        val testTokens = TokenPair("access_token_123", "refresh_token_456", 900L, 604800L)
        val accessTokenCookie = createTestCookie("access_token", "access_token_123")
        val refreshTokenCookie = createTestCookie("refresh_token", "refresh_token_456")

        val eventSlot = slot<UserLoggedIn>()

        every { userRepository.findById(testUserId) } returns Optional.of(testUser)
        every { sessionService.createSession(any(), any(), any(), any(), any()) } returns testSession
        every { tokenService.createTokens(any(), any(), any()) } returns testTokens
        every { authCookieBuilder.buildAccessTokenCookie(testTokens.accessToken) } returns accessTokenCookie
        every { authCookieBuilder.buildRefreshTokenCookie(testTokens.refreshToken) } returns refreshTokenCookie
        every { eventStoreRepository.append(capture(eventSlot)) } just Runs

        // When
        val response = authenticationSessionService.createAuthenticatedSession(
            userId = testUserId,
            ipAddress = testIpAddress,
            userAgent = testUserAgent,
            deviceFingerprint = testDeviceFingerprint,
            rememberDevice = false,
            mfaUsed = true,
            mfaMethod = "TOTP",
            correlationId = testCorrelationId
        )

        // Then
        assertNotNull(response)

        val responseEntity = response.build<Void>()
        val cookies = responseEntity.headers[HttpHeaders.SET_COOKIE]
        assertNotNull(cookies)
        assertEquals(2, cookies.size) // access_token and refresh_token only (no device_trust)
        assertTrue(cookies.any { it.contains("access_token") })
        assertTrue(cookies.any { it.contains("refresh_token") })

        verify(exactly = 1) { userRepository.findById(testUserId) }
        verify(exactly = 1) { sessionService.createSession(any(), any(), testIpAddress, testUserAgent, any()) }
        verify(exactly = 1) { tokenService.createTokens(testUser, testSessionId, any()) }
        verify(exactly = 1) { eventStoreRepository.append(any<UserLoggedIn>()) }
        verify(exactly = 1) { userEventPublisher.publish(any<UserLoggedIn>()) }
        verify(exactly = 0) { deviceTrustService.createTrust(any(), any(), any(), any(), any()) }

        val event = eventSlot.captured
        assertEquals(testUserId, event.payload.userId)
        assertEquals(testSessionId, event.payload.sessionId)
        assertEquals(testIpAddress, event.payload.ipAddress)
        assertEquals(testUserAgent, event.payload.userAgent)
        assertEquals(testDeviceFingerprint, event.payload.deviceFingerprint)
        assertEquals(true, event.payload.mfaUsed)
        assertEquals("TOTP", event.payload.mfaMethod)
    }

    @Test
    fun `createAuthenticatedSession should create device trust when rememberDevice is true`() {
        // Given
        val testUser = createTestUser()
        val testSession = createTestSession()
        val testTokens = TokenPair("access_token_123", "refresh_token_456", 900L, 604800L)
        val testDeviceTrust = createTestDeviceTrust()
        val accessTokenCookie = createTestCookie("access_token", "access_token_123")
        val refreshTokenCookie = createTestCookie("refresh_token", "refresh_token_456")
        val deviceTrustCookie = createTestCookie("device_trust", "trust_token_789")

        every { userRepository.findById(testUserId) } returns Optional.of(testUser)
        every { sessionService.createSession(any(), any(), any(), any(), any()) } returns testSession
        every { tokenService.createTokens(any(), any(), any()) } returns testTokens
        every { authCookieBuilder.buildAccessTokenCookie(testTokens.accessToken) } returns accessTokenCookie
        every { authCookieBuilder.buildRefreshTokenCookie(testTokens.refreshToken) } returns refreshTokenCookie
        every {
            deviceTrustService.createTrust(
                testUserId,
                testDeviceFingerprint,
                testUserAgent,
                testIpAddress,
                testCorrelationId
            )
        } returns testDeviceTrust
        every { authCookieBuilder.buildDeviceTrustCookie(any()) } returns deviceTrustCookie

        // When
        val response = authenticationSessionService.createAuthenticatedSession(
            userId = testUserId,
            ipAddress = testIpAddress,
            userAgent = testUserAgent,
            deviceFingerprint = testDeviceFingerprint,
            rememberDevice = true,
            mfaUsed = true,
            mfaMethod = "SMS",
            correlationId = testCorrelationId
        )

        // Then
        val responseEntity = response.build<Void>()
        val cookies = responseEntity.headers[HttpHeaders.SET_COOKIE]
        assertNotNull(cookies)
        assertEquals(3, cookies.size) // access_token, refresh_token, and device_trust
        assertTrue(cookies.any { it.contains("device_trust") })

        verify(exactly = 1) {
            deviceTrustService.createTrust(
                testUserId,
                testDeviceFingerprint,
                testUserAgent,
                testIpAddress,
                testCorrelationId
            )
        }
        verify(exactly = 1) { authCookieBuilder.buildDeviceTrustCookie(any()) }
    }

    @Test
    fun `createAuthenticatedSession should NOT create device trust when rememberDevice is false`() {
        // Given
        val testUser = createTestUser()
        val testSession = createTestSession()
        val testTokens = TokenPair("access_token_123", "refresh_token_456", 900L, 604800L)
        val accessTokenCookie = createTestCookie("access_token", "access_token_123")
        val refreshTokenCookie = createTestCookie("refresh_token", "refresh_token_456")

        every { userRepository.findById(testUserId) } returns Optional.of(testUser)
        every { sessionService.createSession(any(), any(), any(), any(), any()) } returns testSession
        every { tokenService.createTokens(any(), any(), any()) } returns testTokens
        every { authCookieBuilder.buildAccessTokenCookie(testTokens.accessToken) } returns accessTokenCookie
        every { authCookieBuilder.buildRefreshTokenCookie(testTokens.refreshToken) } returns refreshTokenCookie

        // When
        val response = authenticationSessionService.createAuthenticatedSession(
            userId = testUserId,
            ipAddress = testIpAddress,
            userAgent = testUserAgent,
            deviceFingerprint = testDeviceFingerprint,
            rememberDevice = false,
            mfaUsed = false,
            mfaMethod = null,
            correlationId = testCorrelationId
        )

        // Then
        val responseEntity = response.build<Void>()
        val cookies = responseEntity.headers[HttpHeaders.SET_COOKIE]
        assertNotNull(cookies)
        assertEquals(2, cookies.size) // Only access_token and refresh_token

        verify(exactly = 0) { deviceTrustService.createTrust(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { authCookieBuilder.buildDeviceTrustCookie(any()) }
    }

    @Test
    fun `createAuthenticatedSession should NOT create device trust when deviceFingerprint is null`() {
        // Given
        val testUser = createTestUser()
        val testSession = createTestSession()
        val testTokens = TokenPair("access_token_123", "refresh_token_456", 900L, 604800L)
        val accessTokenCookie = createTestCookie("access_token", "access_token_123")
        val refreshTokenCookie = createTestCookie("refresh_token", "refresh_token_456")

        every { userRepository.findById(testUserId) } returns Optional.of(testUser)
        every { sessionService.createSession(any(), any(), any(), any(), any()) } returns testSession
        every { tokenService.createTokens(any(), any(), any()) } returns testTokens
        every { authCookieBuilder.buildAccessTokenCookie(testTokens.accessToken) } returns accessTokenCookie
        every { authCookieBuilder.buildRefreshTokenCookie(testTokens.refreshToken) } returns refreshTokenCookie

        // When
        val response = authenticationSessionService.createAuthenticatedSession(
            userId = testUserId,
            ipAddress = testIpAddress,
            userAgent = testUserAgent,
            deviceFingerprint = null, // No fingerprint
            rememberDevice = true,
            mfaUsed = false,
            mfaMethod = null,
            correlationId = testCorrelationId
        )

        // Then
        val responseEntity = response.build<Void>()
        val cookies = responseEntity.headers[HttpHeaders.SET_COOKIE]
        assertNotNull(cookies)
        assertEquals(2, cookies.size) // Only access_token and refresh_token

        verify(exactly = 0) { deviceTrustService.createTrust(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { authCookieBuilder.buildDeviceTrustCookie(any()) }
    }

    @Test
    fun `createAuthenticatedSession should set correct cookie attributes`() {
        // Given
        val testUser = createTestUser()
        val testSession = createTestSession()
        val testTokens = TokenPair("access_token_123", "refresh_token_456", 900L, 604800L)

        val accessTokenCookie = ResponseCookie.from("access_token", "access_token_123")
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/")
            .maxAge(900)
            .build()

        val refreshTokenCookie = ResponseCookie.from("refresh_token", "refresh_token_456")
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/api/v1/auth/refresh")
            .maxAge(604800)
            .build()

        every { userRepository.findById(testUserId) } returns Optional.of(testUser)
        every { sessionService.createSession(any(), any(), any(), any(), any()) } returns testSession
        every { tokenService.createTokens(any(), any(), any()) } returns testTokens
        every { authCookieBuilder.buildAccessTokenCookie(testTokens.accessToken) } returns accessTokenCookie
        every { authCookieBuilder.buildRefreshTokenCookie(testTokens.refreshToken) } returns refreshTokenCookie

        // When
        val response = authenticationSessionService.createAuthenticatedSession(
            userId = testUserId,
            ipAddress = testIpAddress,
            userAgent = testUserAgent,
            deviceFingerprint = null,
            rememberDevice = false,
            mfaUsed = false,
            mfaMethod = null,
            correlationId = testCorrelationId
        )

        // Then
        val responseEntity = response.build<Void>()
        val cookies = responseEntity.headers[HttpHeaders.SET_COOKIE]
        assertNotNull(cookies)

        val accessCookie = cookies.find { it.contains("access_token") }
        assertNotNull(accessCookie)
        assertTrue(accessCookie.contains("HttpOnly"))
        assertTrue(accessCookie.contains("Secure"))
        assertTrue(accessCookie.contains("SameSite=Strict"))

        verify(exactly = 1) { authCookieBuilder.buildAccessTokenCookie(testTokens.accessToken) }
        verify(exactly = 1) { authCookieBuilder.buildRefreshTokenCookie(testTokens.refreshToken) }
    }

    @Test
    fun `createAuthenticatedSession should publish UserLoggedIn event with correct data`() {
        // Given
        val testUser = createTestUser()
        val testSession = createTestSession()
        val testTokens = TokenPair("access_token_123", "refresh_token_456", 900L, 604800L)
        val accessTokenCookie = createTestCookie("access_token", "access_token_123")
        val refreshTokenCookie = createTestCookie("refresh_token", "refresh_token_456")

        val eventSlot = slot<UserLoggedIn>()

        every { userRepository.findById(testUserId) } returns Optional.of(testUser)
        every { sessionService.createSession(any(), any(), any(), any(), any()) } returns testSession
        every { tokenService.createTokens(any(), any(), any()) } returns testTokens
        every { authCookieBuilder.buildAccessTokenCookie(testTokens.accessToken) } returns accessTokenCookie
        every { authCookieBuilder.buildRefreshTokenCookie(testTokens.refreshToken) } returns refreshTokenCookie
        every { eventStoreRepository.append(capture(eventSlot)) } just Runs

        // When
        authenticationSessionService.createAuthenticatedSession(
            userId = testUserId,
            ipAddress = testIpAddress,
            userAgent = testUserAgent,
            deviceFingerprint = testDeviceFingerprint,
            rememberDevice = false,
            mfaUsed = false,
            mfaMethod = null,
            correlationId = testCorrelationId
        )

        // Then
        val event = eventSlot.captured
        assertEquals(testUserId, event.payload.userId)
        assertEquals(testSessionId, event.payload.sessionId)
        assertEquals(testIpAddress, event.payload.ipAddress)
        assertEquals(testUserAgent, event.payload.userAgent)
        assertEquals(testDeviceFingerprint, event.payload.deviceFingerprint)
        assertEquals(false, event.payload.mfaUsed)
        assertNull(event.payload.mfaMethod)
        assertEquals("WEB", event.payload.loginSource)
        assertEquals(testCorrelationId, event.correlationId)

        verify(exactly = 1) { eventStoreRepository.append(event) }
        verify(exactly = 1) { userEventPublisher.publish(event) }
    }

    @Test
    fun `createAuthenticatedSession should throw IllegalStateException when user not found`() {
        // Given
        every { userRepository.findById(testUserId) } returns Optional.empty()

        // When & Then
        val exception = assertFailsWith<IllegalStateException> {
            authenticationSessionService.createAuthenticatedSession(
                userId = testUserId,
                ipAddress = testIpAddress,
                userAgent = testUserAgent,
                deviceFingerprint = testDeviceFingerprint,
                rememberDevice = false,
                mfaUsed = false,
                mfaMethod = null,
                correlationId = testCorrelationId
            )
        }

        assertTrue(exception.message!!.contains("User not found after successful authentication"))
        assertTrue(exception.message!!.contains(testUserId.toString()))

        verify(exactly = 1) { userRepository.findById(testUserId) }
        verify(exactly = 0) { sessionService.createSession(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { tokenService.createTokens(any(), any(), any()) }
    }

    private fun createTestUser(): User {
        return mockk<User>(relaxed = true) {
            every { id } returns testUserId
        }
    }

    private fun createTestSession(): Session {
        return Session(
            id = testSessionId,
            userId = testUserId,
            deviceId = "dev_test_123",
            ipAddress = testIpAddress,
            userAgent = testUserAgent,
            tokenFamily = "fam_${UUID.randomUUID()}",
            createdAt = Instant.now(),
            expiresAt = Instant.now().plusSeconds(604800),
            ttl = 604800
        )
    }

    private fun createTestDeviceTrust(): DeviceTrust {
        val trustId = "trust_${UUID.randomUUID()}"
        return mockk<DeviceTrust> {
            every { id } returns trustId
        }
    }

    private fun createTestCookie(name: String, value: String): ResponseCookie {
        return ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/")
            .maxAge(3600)
            .build()
    }
}
