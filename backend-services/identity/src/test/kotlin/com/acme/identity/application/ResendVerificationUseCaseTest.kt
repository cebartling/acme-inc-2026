package com.acme.identity.application

import arrow.core.getOrElse
import com.acme.identity.domain.RegistrationSource
import com.acme.identity.domain.User
import com.acme.identity.domain.UserStatus
import com.acme.identity.domain.VerificationResendRequest
import com.acme.identity.domain.VerificationToken
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.EventStoreRepository
import com.acme.identity.infrastructure.persistence.ResendRequestRepository
import com.acme.identity.infrastructure.persistence.UserRepository
import com.acme.identity.infrastructure.persistence.VerificationTokenRepository
import com.acme.identity.infrastructure.ratelimit.RateLimitResult
import com.acme.identity.infrastructure.ratelimit.VerificationRateLimiter
import com.acme.identity.infrastructure.security.VerificationTokenGenerator
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class ResendVerificationUseCaseTest {

    private lateinit var userRepository: UserRepository
    private lateinit var verificationTokenRepository: VerificationTokenRepository
    private lateinit var resendRequestRepository: ResendRequestRepository
    private lateinit var eventStoreRepository: EventStoreRepository
    private lateinit var userEventPublisher: UserEventPublisher
    private lateinit var verificationTokenGenerator: VerificationTokenGenerator
    private lateinit var verificationRateLimiter: VerificationRateLimiter
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var resendVerificationUseCase: ResendVerificationUseCase

    private val testUserId = UUID.randomUUID()
    private val testEmail = "customer@example.com"
    private val testToken = "new-token-xyz789"

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        verificationTokenRepository = mockk()
        resendRequestRepository = mockk()
        eventStoreRepository = mockk()
        userEventPublisher = mockk()
        verificationTokenGenerator = mockk()
        verificationRateLimiter = mockk()
        meterRegistry = SimpleMeterRegistry()

        resendVerificationUseCase = ResendVerificationUseCase(
            userRepository = userRepository,
            verificationTokenRepository = verificationTokenRepository,
            resendRequestRepository = resendRequestRepository,
            eventStoreRepository = eventStoreRepository,
            userEventPublisher = userEventPublisher,
            verificationTokenGenerator = verificationTokenGenerator,
            verificationRateLimiter = verificationRateLimiter,
            meterRegistry = meterRegistry
        )
    }

    @Test
    fun `execute should successfully resend verification email`() {
        // Given
        val user = createPendingUser()
        val expiration = Instant.now().plusSeconds(86400)

        every { verificationRateLimiter.checkRateLimit(testEmail) } returns RateLimitResult.Allowed(remaining = 2)
        every { resendRequestRepository.save(any()) } answers { firstArg() }
        every { verificationRateLimiter.getRateLimitInfo(testEmail) } returns Pair(2, 3)
        every { userRepository.findByEmail(testEmail) } returns user
        every { verificationTokenGenerator.generate() } returns testToken
        every { verificationTokenGenerator.calculateExpiration() } returns expiration
        every { verificationTokenRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publish(any()) } returns CompletableFuture.completedFuture(null)

        // When
        val result = resendVerificationUseCase.execute(testEmail)

        // Then
        assertTrue(result.isRight())
        val success = result.getOrElse { fail("Expected success but got error") }
        assertTrue(success.message.contains("verification link has been sent"))
        assertEquals(2, success.requestsRemaining)

        verify(exactly = 1) { verificationTokenRepository.save(any()) }
        verify(exactly = 1) { eventStoreRepository.append(any()) }
        verify(exactly = 1) { userEventPublisher.publish(any()) }
    }

    @Test
    fun `execute should return RateLimited when rate limit exceeded`() {
        // Given
        val retryAfter = Instant.now().plusSeconds(1800)
        every { verificationRateLimiter.checkRateLimit(testEmail) } returns RateLimitResult.Exceeded(
            remaining = 0,
            retryAfter = retryAfter
        )

        // When
        val result = resendVerificationUseCase.execute(testEmail)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertIs<ResendError.RateLimited>(error)
                assertEquals(retryAfter, error.retryAfter)
            },
            ifRight = { fail("Expected error but got success") }
        )

        verify(exactly = 0) { userRepository.findByEmail(any()) }
        verify(exactly = 0) { eventStoreRepository.append(any()) }
    }

    @Test
    fun `execute should return success even when email does not exist for security`() {
        // Given
        every { verificationRateLimiter.checkRateLimit(any()) } returns RateLimitResult.Allowed(remaining = 2)
        every { resendRequestRepository.save(any()) } answers { firstArg() }
        every { verificationRateLimiter.getRateLimitInfo(any()) } returns Pair(2, 3)
        every { userRepository.findByEmail(any()) } returns null

        // When
        val result = resendVerificationUseCase.execute("nonexistent@example.com")

        // Then
        assertTrue(result.isRight())
        val success = result.getOrElse { fail("Expected success but got error") }
        assertTrue(success.message.contains("verification link has been sent"))

        // Should not attempt to create token or publish event
        verify(exactly = 0) { verificationTokenRepository.save(any()) }
        verify(exactly = 0) { eventStoreRepository.append(any()) }
    }

    @Test
    fun `execute should return success when user is already verified for security`() {
        // Given
        val verifiedUser = User(
            id = testUserId,
            email = testEmail,
            passwordHash = "hash",
            firstName = "Jane",
            lastName = "Doe",
            status = UserStatus.ACTIVE,
            tosAcceptedAt = Instant.now(),
            registrationSource = RegistrationSource.WEB,
            emailVerified = true,
            verifiedAt = Instant.now().minusSeconds(3600)
        )

        every { verificationRateLimiter.checkRateLimit(testEmail) } returns RateLimitResult.Allowed(remaining = 2)
        every { resendRequestRepository.save(any()) } answers { firstArg() }
        every { verificationRateLimiter.getRateLimitInfo(testEmail) } returns Pair(2, 3)
        every { userRepository.findByEmail(testEmail) } returns verifiedUser

        // When
        val result = resendVerificationUseCase.execute(testEmail)

        // Then
        assertTrue(result.isRight())
        val success = result.getOrElse { fail("Expected success but got error") }
        assertTrue(success.message.contains("verification link has been sent"))

        // Should not create token for already-verified user
        verify(exactly = 0) { verificationTokenRepository.save(any()) }
        verify(exactly = 0) { eventStoreRepository.append(any()) }
    }

    @Test
    fun `execute should record resend request for rate limiting`() {
        // Given
        val user = createPendingUser()
        val savedRequest = slot<VerificationResendRequest>()
        val expiration = Instant.now().plusSeconds(86400)

        every { verificationRateLimiter.checkRateLimit(testEmail) } returns RateLimitResult.Allowed(remaining = 2)
        every { resendRequestRepository.save(capture(savedRequest)) } answers { firstArg() }
        every { verificationRateLimiter.getRateLimitInfo(testEmail) } returns Pair(2, 3)
        every { userRepository.findByEmail(testEmail) } returns user
        every { verificationTokenGenerator.generate() } returns testToken
        every { verificationTokenGenerator.calculateExpiration() } returns expiration
        every { verificationTokenRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publish(any()) } returns CompletableFuture.completedFuture(null)

        // When
        resendVerificationUseCase.execute(testEmail, "192.168.1.1")

        // Then
        assertEquals(testEmail, savedRequest.captured.email)
        assertEquals("192.168.1.1", savedRequest.captured.ipAddress)
    }

    @Test
    fun `execute should generate new verification token`() {
        // Given
        val user = createPendingUser()
        val expiration = Instant.now().plusSeconds(86400)
        val savedToken = slot<VerificationToken>()

        every { verificationRateLimiter.checkRateLimit(testEmail) } returns RateLimitResult.Allowed(remaining = 2)
        every { resendRequestRepository.save(any()) } answers { firstArg() }
        every { verificationRateLimiter.getRateLimitInfo(testEmail) } returns Pair(2, 3)
        every { userRepository.findByEmail(testEmail) } returns user
        every { verificationTokenGenerator.generate() } returns testToken
        every { verificationTokenGenerator.calculateExpiration() } returns expiration
        every { verificationTokenRepository.save(capture(savedToken)) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publish(any()) } returns CompletableFuture.completedFuture(null)

        // When
        resendVerificationUseCase.execute(testEmail)

        // Then
        assertEquals(testToken, savedToken.captured.token)
        assertEquals(testUserId, savedToken.captured.userId)
        assertEquals(expiration, savedToken.captured.expiresAt)
    }

    @Test
    fun `execute should record metrics on success`() {
        // Given
        val user = createPendingUser()
        val expiration = Instant.now().plusSeconds(86400)

        every { verificationRateLimiter.checkRateLimit(testEmail) } returns RateLimitResult.Allowed(remaining = 2)
        every { resendRequestRepository.save(any()) } answers { firstArg() }
        every { verificationRateLimiter.getRateLimitInfo(testEmail) } returns Pair(2, 3)
        every { userRepository.findByEmail(testEmail) } returns user
        every { verificationTokenGenerator.generate() } returns testToken
        every { verificationTokenGenerator.calculateExpiration() } returns expiration
        every { verificationTokenRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publish(any()) } returns CompletableFuture.completedFuture(null)

        // When
        resendVerificationUseCase.execute(testEmail)

        // Then
        val counter = meterRegistry.counter("resend_verification_total", "result", "success")
        assertEquals(1.0, counter.count())
    }

    @Test
    fun `execute should record metrics on rate limit`() {
        // Given
        val retryAfter = Instant.now().plusSeconds(1800)
        every { verificationRateLimiter.checkRateLimit(testEmail) } returns RateLimitResult.Exceeded(
            remaining = 0,
            retryAfter = retryAfter
        )

        // When
        resendVerificationUseCase.execute(testEmail)

        // Then
        val counter = meterRegistry.counter("resend_verification_total", "result", "rate_limited")
        assertEquals(1.0, counter.count())
    }

    private fun createPendingUser(): User {
        return User(
            id = testUserId,
            email = testEmail,
            passwordHash = "\$argon2id\$v=19\$m=65536,t=3,p=4\$somesalt\$somehash",
            firstName = "Jane",
            lastName = "Doe",
            status = UserStatus.PENDING_VERIFICATION,
            tosAcceptedAt = Instant.now(),
            registrationSource = RegistrationSource.WEB
        )
    }
}
