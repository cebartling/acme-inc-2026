package com.acme.identity.application

import com.acme.identity.domain.RegistrationSource
import com.acme.identity.domain.Session
import com.acme.identity.domain.User
import com.acme.identity.domain.UserStatus
import com.acme.identity.infrastructure.persistence.SessionRepository
import com.acme.identity.infrastructure.persistence.UserRepository
import com.nimbusds.jwt.JWTClaimsSet
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RefreshTokensUseCaseTest {
    private lateinit var tokenService: TokenService
    private lateinit var sessionRepository: SessionRepository
    private lateinit var userRepository: UserRepository
    private lateinit var useCase: RefreshTokensUseCase

    private val sessionId = "sess_${UUID.randomUUID()}"
    private val userId = UUID.randomUUID()
    private val originalFamily = "fam_${UUID.randomUUID()}"

    @BeforeEach
    fun setUp() {
        tokenService = mockk()
        sessionRepository = mockk()
        userRepository = mockk()

        useCase = RefreshTokensUseCase(
            tokenService = tokenService,
            sessionRepository = sessionRepository,
            userRepository = userRepository,
            meterRegistry = SimpleMeterRegistry()
        )
    }

    @Test
    fun `returns MissingToken when no refresh token is provided`() {
        val result = useCase.execute(null)
        assertEquals(RefreshResult.MissingToken, result)
    }

    @Test
    fun `returns MissingToken for blank refresh token`() {
        val result = useCase.execute("   ")
        assertEquals(RefreshResult.MissingToken, result)
    }

    @Test
    fun `returns InvalidToken when JWT fails parse or verify`() {
        every { tokenService.parseRefreshTokenClaims("bad.jwt.value") } returns null

        val result = useCase.execute("bad.jwt.value")

        assertEquals(RefreshResult.InvalidToken, result)
    }

    @Test
    fun `returns InvalidToken when claims are missing required fields`() {
        // Missing sessionId
        val claims = JWTClaimsSet.Builder()
            .subject(userId.toString())
            .claim("tokenFamily", originalFamily)
            .build()
        every { tokenService.parseRefreshTokenClaims(any()) } returns claims

        val result = useCase.execute("token")

        assertEquals(RefreshResult.InvalidToken, result)
    }

    @Test
    fun `returns SessionNotFound when the session was evicted from Redis`() {
        every { tokenService.parseRefreshTokenClaims(any()) } returns claims(userId, sessionId, originalFamily)
        every { sessionRepository.findById(sessionId) } returns Optional.empty()

        val result = useCase.execute("token")

        assertEquals(RefreshResult.SessionNotFound, result)
    }

    @Test
    fun `returns TokenReuse when JWT tokenFamily mismatches the session's current family`() {
        val sessionAfterRotation = sessionFixture(tokenFamily = "fam_NEW_$originalFamily")
        every { tokenService.parseRefreshTokenClaims(any()) } returns claims(userId, sessionId, originalFamily)
        every { sessionRepository.findById(sessionId) } returns Optional.of(sessionAfterRotation)

        val result = useCase.execute("stale_token")

        assertEquals(RefreshResult.TokenReuse, result)
        verify(exactly = 0) { tokenService.createTokens(any(), any(), any()) }
        verify(exactly = 0) { sessionRepository.save(any()) }
    }

    @Test
    fun `rotates tokens and updates session family on happy path`() {
        val session = sessionFixture(tokenFamily = originalFamily)
        val user = testUser()
        every {
            tokenService.parseRefreshTokenClaims(any())
        } returns claims(userId, sessionId, originalFamily)
        every { sessionRepository.findById(sessionId) } returns Optional.of(session)
        every { userRepository.findById(userId) } returns Optional.of(user)

        val sessionSlot = slot<Session>()
        every { sessionRepository.save(capture(sessionSlot)) } answers { sessionSlot.captured }

        val tokenFamilySlot = slot<String>()
        every {
            tokenService.createTokens(user, sessionId, capture(tokenFamilySlot))
        } answers {
            TokenPair(
                accessToken = "new_access",
                refreshToken = "new_refresh",
                accessTokenExpiry = 900,
                refreshTokenExpiry = 604800
            )
        }

        val result = useCase.execute("valid_token")

        val success = assertIs<RefreshResult.Success>(result)
        assertEquals("new_access", success.tokens.accessToken)
        assertEquals("new_refresh", success.tokens.refreshToken)
        assertEquals(sessionId, success.sessionId)
        // tokenFamily on success matches what was passed to createTokens AND saved to Redis.
        assertEquals(tokenFamilySlot.captured, success.newTokenFamily)
        assertEquals(success.newTokenFamily, sessionSlot.captured.tokenFamily)
        // The rotated tokenFamily must differ from the original.
        assertNotEquals(originalFamily, success.newTokenFamily)
        // tokenFamily looks like a UUID-prefixed identifier.
        assertTrue(success.newTokenFamily.startsWith("fam_"))
        // The rest of the session is untouched.
        assertEquals(session.id, sessionSlot.captured.id)
        assertEquals(session.userId, sessionSlot.captured.userId)
        assertEquals(session.createdAt, sessionSlot.captured.createdAt)
    }

    @Test
    fun `returns InvalidToken when the user no longer exists`() {
        val session = sessionFixture(tokenFamily = originalFamily)
        every {
            tokenService.parseRefreshTokenClaims(any())
        } returns claims(userId, sessionId, originalFamily)
        every { sessionRepository.findById(sessionId) } returns Optional.of(session)
        every { userRepository.findById(userId) } returns Optional.empty()

        val result = useCase.execute("valid_token")

        assertEquals(RefreshResult.InvalidToken, result)
        verify(exactly = 0) { sessionRepository.save(any()) }
    }

    // --- fixtures ---

    private fun claims(userId: UUID, sessionId: String, tokenFamily: String): JWTClaimsSet =
        JWTClaimsSet.Builder()
            .subject(userId.toString())
            .claim("sessionId", sessionId)
            .claim("tokenFamily", tokenFamily)
            .build()

    private fun sessionFixture(tokenFamily: String) = Session(
        id = sessionId,
        userId = userId,
        deviceId = "test_device",
        ipAddress = "127.0.0.1",
        userAgent = "test-agent",
        tokenFamily = tokenFamily,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        expiresAt = Instant.parse("2026-01-08T00:00:00Z"),
        ttl = 604800
    )

    private fun testUser() = User(
        id = userId,
        email = "user@example.com",
        passwordHash = "hash",
        firstName = "Test",
        lastName = "User",
        status = UserStatus.ACTIVE,
        tosAcceptedAt = Instant.now(),
        marketingOptIn = false,
        registrationSource = RegistrationSource.WEB
    )
}
