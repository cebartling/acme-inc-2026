package com.acme.identity.application

import com.acme.identity.domain.RegistrationSource
import com.acme.identity.domain.Session
import com.acme.identity.domain.User
import com.acme.identity.domain.UserStatus
import com.acme.identity.domain.events.SessionInvalidated
import com.acme.identity.domain.events.TokenReuseDetected
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.EventStoreRepository
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
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RefreshTokensUseCaseTest {
    private lateinit var tokenService: TokenService
    private lateinit var sessionRepository: SessionRepository
    private lateinit var userRepository: UserRepository
    private lateinit var eventStore: EventStoreRepository
    private lateinit var publisher: UserEventPublisher
    private lateinit var useCase: RefreshTokensUseCase

    private val sessionId = "sess_${UUID.randomUUID()}"
    private val userId = UUID.randomUUID()
    private val originalFamily = "fam_${UUID.randomUUID()}"

    @BeforeEach
    fun setUp() {
        tokenService = mockk()
        sessionRepository = mockk(relaxed = true)
        userRepository = mockk()
        eventStore = mockk(relaxed = true)
        publisher = mockk(relaxed = true)

        useCase = RefreshTokensUseCase(
            tokenService = tokenService,
            sessionRepository = sessionRepository,
            userRepository = userRepository,
            eventStoreRepository = eventStore,
            userEventPublisher = publisher,
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
        every { sessionRepository.findByUserId(userId) } returns listOf(sessionAfterRotation)
        every { publisher.publish(any<SessionInvalidated>()) } returns CompletableFuture.completedFuture(null)
        every { publisher.publishTokenReuseDetected(any()) } returns CompletableFuture.completedFuture(null)

        val result = useCase.execute("stale_token")

        assertEquals(RefreshResult.TokenReuse, result)
        verify(exactly = 0) { tokenService.createTokens(any(), any(), any()) }
        verify(exactly = 0) { sessionRepository.save(any()) }
    }

    @Test
    fun `reuse detection invalidates every session for the user (OWASP) and publishes events`() {
        // Two sessions: the one whose refresh token was replayed, and a
        // separate session the same user has on another device. Both must
        // be killed on reuse detection per OWASP guidance.
        val triggering = sessionFixture(tokenFamily = "fam_CURRENT_$originalFamily")
        val otherSessionId = "sess_${UUID.randomUUID()}"
        val other = Session(
            id = otherSessionId,
            userId = userId,
            deviceId = "other_device",
            ipAddress = "10.0.0.1",
            userAgent = "other-agent",
            tokenFamily = "fam_OTHER",
            createdAt = Instant.now(),
            expiresAt = Instant.now().plusSeconds(604800),
            ttl = 604800
        )
        every { tokenService.parseRefreshTokenClaims(any()) } returns claims(userId, sessionId, originalFamily)
        every { sessionRepository.findById(sessionId) } returns Optional.of(triggering)
        every { sessionRepository.findByUserId(userId) } returns listOf(triggering, other)
        every { publisher.publish(any<SessionInvalidated>()) } returns CompletableFuture.completedFuture(null)
        every { publisher.publishTokenReuseDetected(any()) } returns CompletableFuture.completedFuture(null)

        val result = useCase.execute("stale_token")

        assertEquals(RefreshResult.TokenReuse, result)

        // Both sessions deleted.
        verify(exactly = 1) { sessionRepository.delete(triggering) }
        verify(exactly = 1) { sessionRepository.delete(other) }

        // One SessionInvalidated event per session, each with reason=SECURITY.
        val invalidationSlots = mutableListOf<SessionInvalidated>()
        verify(exactly = 2) { publisher.publish(capture(invalidationSlots)) }
        assertTrue(
            invalidationSlots.all { it.payload.reason == SessionInvalidated.REASON_SECURITY },
            "every invalidation event from reuse detection should have reason=SECURITY"
        )
        val invalidatedIds = invalidationSlots.map { it.payload.sessionId }.toSet()
        assertEquals(setOf(sessionId, otherSessionId), invalidatedIds)

        // Exactly one TokenReuseDetected event, sized to the sweep.
        val reuseSlot = slot<TokenReuseDetected>()
        verify(exactly = 1) { publisher.publishTokenReuseDetected(capture(reuseSlot)) }
        assertEquals(sessionId, reuseSlot.captured.payload.sessionId)
        assertEquals(userId, reuseSlot.captured.payload.userId)
        assertEquals(originalFamily, reuseSlot.captured.payload.presentedTokenFamily)
        assertEquals(triggering.tokenFamily, reuseSlot.captured.payload.sessionTokenFamily)
        assertEquals(2, reuseSlot.captured.payload.sessionsInvalidatedCount)

        // No new tokens issued, no session saved.
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
