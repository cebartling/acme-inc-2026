package com.acme.identity.application

import com.acme.identity.domain.PasswordResetToken
import com.acme.identity.domain.RegistrationSource
import com.acme.identity.domain.User
import com.acme.identity.domain.UserStatus
import com.acme.identity.domain.events.PasswordResetRequested
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.EventStoreRepository
import com.acme.identity.infrastructure.persistence.PasswordResetTokenRepository
import com.acme.identity.infrastructure.persistence.UserRepository
import com.acme.identity.infrastructure.ratelimit.PasswordResetRateLimiter
import com.acme.identity.infrastructure.ratelimit.RateLimitResult
import com.acme.identity.infrastructure.security.PasswordResetTokenGenerator
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals

class RequestPasswordResetUseCaseTest {
    private lateinit var userRepository: UserRepository
    private lateinit var tokenRepository: PasswordResetTokenRepository
    private lateinit var eventStore: EventStoreRepository
    private lateinit var publisher: UserEventPublisher
    private lateinit var tokenGenerator: PasswordResetTokenGenerator
    private lateinit var rateLimiter: PasswordResetRateLimiter
    private lateinit var useCase: RequestPasswordResetUseCase

    private val expiresAt = Instant.parse("2026-06-01T00:00:00Z")

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        tokenRepository = mockk()
        eventStore = mockk(relaxed = true)
        publisher = mockk(relaxed = true)
        tokenGenerator = mockk()
        rateLimiter = mockk(relaxed = true)

        every { tokenGenerator.generate() } returns "rst_test_token"
        every { tokenGenerator.calculateExpiration() } returns expiresAt
        every { tokenRepository.save(any()) } answers { firstArg() }
        every { rateLimiter.checkRateLimit(any()) } returns RateLimitResult.Allowed(remaining = 3)
        every {
            publisher.publishPasswordResetRequested(any())
        } returns CompletableFuture.completedFuture(null)

        useCase = RequestPasswordResetUseCase(
            userRepository = userRepository,
            passwordResetTokenRepository = tokenRepository,
            eventStoreRepository = eventStore,
            userEventPublisher = publisher,
            tokenGenerator = tokenGenerator,
            rateLimiter = rateLimiter,
            meterRegistry = SimpleMeterRegistry()
        )
    }

    @Test
    fun `issues token and publishes event for known user`() {
        val user = activeUser("customer@example.com")
        every { userRepository.findByEmail("customer@example.com") } returns user

        val tokenSlot = slot<PasswordResetToken>()
        every { tokenRepository.save(capture(tokenSlot)) } answers { tokenSlot.captured }

        val result = useCase.execute("customer@example.com", ipAddress = "127.0.0.1")

        assertEquals(RequestPasswordResetResult.EmailSent, result)
        assertEquals("rst_test_token", tokenSlot.captured.token)
        assertEquals(user.id, tokenSlot.captured.userId)

        val eventSlot = slot<PasswordResetRequested>()
        verify { eventStore.append(capture(eventSlot)) }
        assertEquals(user.id, eventSlot.captured.payload.userId)
        assertEquals("rst_test_token", eventSlot.captured.payload.resetToken)
        verify { publisher.publishPasswordResetRequested(any()) }
    }

    @Test
    fun `still records rate-limit attempt and returns success for unknown email`() {
        every { userRepository.findByEmail("ghost@example.com") } returns null

        val result = useCase.execute("ghost@example.com", ipAddress = "127.0.0.1")

        assertEquals(RequestPasswordResetResult.UnknownEmail, result)
        verify { rateLimiter.record("ghost@example.com", "127.0.0.1") }
        verify(exactly = 0) { tokenRepository.save(any()) }
        verify(exactly = 0) { publisher.publishPasswordResetRequested(any()) }
    }

    @Test
    fun `returns RateLimited and emits nothing when limit exceeded`() {
        every { rateLimiter.checkRateLimit(any()) } returns
            RateLimitResult.Exceeded(retryAfter = Instant.now())

        val result = useCase.execute("customer@example.com", ipAddress = "127.0.0.1")

        assertEquals(RequestPasswordResetResult.RateLimited, result)
        verify(exactly = 0) { rateLimiter.record(any(), any()) }
        verify(exactly = 0) { userRepository.findByEmail(any()) }
        verify(exactly = 0) { tokenRepository.save(any()) }
    }

    @Test
    fun `lowercases the email before lookup`() {
        every { userRepository.findByEmail("user@example.com") } returns null

        useCase.execute("User@Example.COM", ipAddress = null)

        verify { userRepository.findByEmail("user@example.com") }
    }

    private fun activeUser(email: String): User = User(
        id = UUID.randomUUID(),
        email = email,
        passwordHash = "hash",
        firstName = "Test",
        lastName = "User",
        status = UserStatus.ACTIVE,
        tosAcceptedAt = Instant.now(),
        marketingOptIn = false,
        registrationSource = RegistrationSource.WEB
    )
}
