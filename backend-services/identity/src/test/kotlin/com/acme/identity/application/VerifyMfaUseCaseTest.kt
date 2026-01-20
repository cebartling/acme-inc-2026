package com.acme.identity.application

import arrow.core.getOrElse
import com.acme.identity.domain.MfaChallenge
import com.acme.identity.domain.MfaMethod
import com.acme.identity.domain.RegistrationSource
import com.acme.identity.domain.User
import com.acme.identity.domain.UserStatus
import com.acme.identity.domain.UsedTotpCode
import com.acme.identity.domain.events.MFAChallengeInitiated
import com.acme.identity.domain.events.MFAVerificationFailed
import com.acme.identity.domain.events.MFAVerificationSucceeded
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.EventStoreRepository
import com.acme.identity.infrastructure.persistence.MfaChallengeRepository
import com.acme.identity.infrastructure.persistence.UsedTotpCodeRepository
import com.acme.identity.infrastructure.persistence.UserRepository
import com.acme.identity.infrastructure.security.TotpService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class VerifyMfaUseCaseTest {

    private lateinit var mfaChallengeRepository: MfaChallengeRepository
    private lateinit var usedTotpCodeRepository: UsedTotpCodeRepository
    private lateinit var userRepository: UserRepository
    private lateinit var eventStoreRepository: EventStoreRepository
    private lateinit var userEventPublisher: UserEventPublisher
    private lateinit var totpService: TotpService
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var verifyMfaUseCase: VerifyMfaUseCase

    private val testUserId = UUID.randomUUID()
    private val testMfaToken = "mfa_${UUID.randomUUID()}"
    private val testTotpSecret = "JBSWY3DPEHPK3PXP" // Base32 encoded secret

    @BeforeEach
    fun setUp() {
        mfaChallengeRepository = mockk()
        usedTotpCodeRepository = mockk()
        userRepository = mockk()
        eventStoreRepository = mockk()
        userEventPublisher = mockk()
        totpService = TotpService() // Use real TOTP service for accuracy
        meterRegistry = SimpleMeterRegistry()

        verifyMfaUseCase = VerifyMfaUseCase(
            mfaChallengeRepository = mfaChallengeRepository,
            usedTotpCodeRepository = usedTotpCodeRepository,
            userRepository = userRepository,
            eventStoreRepository = eventStoreRepository,
            userEventPublisher = userEventPublisher,
            totpService = totpService,
            meterRegistry = meterRegistry
        )

        // Default mock setups
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishMFAVerificationSucceeded(any()) } returns CompletableFuture.completedFuture(null)
        every { userEventPublisher.publishMFAVerificationFailed(any()) } returns CompletableFuture.completedFuture(null)
        every { userEventPublisher.publishMFAChallengeInitiated(any()) } returns CompletableFuture.completedFuture(null)
    }

    @Test
    fun `execute should successfully verify valid TOTP code`() {
        // Given
        val challenge = createValidChallenge()
        val user = createUserWithTotp()
        val validCode = totpService.generateCode(testTotpSecret)

        every { mfaChallengeRepository.findByToken(testMfaToken) } returns challenge
        every { userRepository.findById(testUserId) } returns Optional.of(user)
        every { usedTotpCodeRepository.existsByUserIdAndCodeHashAndTimeStep(any(), any(), any()) } returns false
        every { usedTotpCodeRepository.save(any()) } answers { firstArg() }
        every { mfaChallengeRepository.delete(challenge) } just Runs

        val request = createVerificationRequest(code = validCode)
        val context = createContext()

        // When
        val result = verifyMfaUseCase.execute(request, context)

        // Then
        assertTrue(result.isRight())
        val success = result.getOrElse { fail("Expected success") }
        assertEquals(testUserId, success.userId)
        assertEquals(false, success.deviceRemembered)

        // Verify challenge was deleted
        verify(exactly = 1) { mfaChallengeRepository.delete(challenge) }

        // Verify code was marked as used
        verify(exactly = 1) { usedTotpCodeRepository.save(any()) }

        // Verify success event was published
        verify(exactly = 1) { eventStoreRepository.append(any<MFAVerificationSucceeded>()) }
        verify(exactly = 1) { userEventPublisher.publishMFAVerificationSucceeded(any()) }
    }

    @Test
    fun `execute should return InvalidToken when challenge not found`() {
        // Given
        every { mfaChallengeRepository.findByToken(testMfaToken) } returns null

        val request = createVerificationRequest()
        val context = createContext()

        // When
        val result = verifyMfaUseCase.execute(request, context)

        // Then
        assertTrue(result.isLeft())
        assertIs<MfaVerificationError.InvalidToken>(result.leftOrNull())

        // Verify counter incremented
        val counter = meterRegistry.counter("mfa_verification_attempts_total", "status", "invalid_token")
        assertEquals(1.0, counter.count())
    }

    @Test
    fun `execute should return Expired when challenge has expired`() {
        // Given
        val expiredChallenge = MfaChallenge(
            id = UUID.randomUUID(),
            userId = testUserId,
            token = testMfaToken,
            method = MfaMethod.TOTP,
            expiresAt = Instant.now().minusSeconds(60), // Expired 1 minute ago
            attempts = 0,
            maxAttempts = 3
        )

        every { mfaChallengeRepository.findByToken(testMfaToken) } returns expiredChallenge
        every { mfaChallengeRepository.delete(expiredChallenge) } just Runs

        val request = createVerificationRequest()
        val context = createContext()

        // When
        val result = verifyMfaUseCase.execute(request, context)

        // Then
        assertTrue(result.isLeft())
        assertIs<MfaVerificationError.Expired>(result.leftOrNull())

        // Verify challenge was deleted
        verify(exactly = 1) { mfaChallengeRepository.delete(expiredChallenge) }

        // Verify failed event was published
        verify(exactly = 1) { eventStoreRepository.append(any<MFAVerificationFailed>()) }
    }

    @Test
    fun `execute should return Expired when max attempts exceeded`() {
        // Given
        val maxedOutChallenge = MfaChallenge(
            id = UUID.randomUUID(),
            userId = testUserId,
            token = testMfaToken,
            method = MfaMethod.TOTP,
            expiresAt = Instant.now().plusSeconds(300),
            attempts = 3, // Already at max
            maxAttempts = 3
        )

        every { mfaChallengeRepository.findByToken(testMfaToken) } returns maxedOutChallenge
        every { mfaChallengeRepository.delete(maxedOutChallenge) } just Runs

        val request = createVerificationRequest()
        val context = createContext()

        // When
        val result = verifyMfaUseCase.execute(request, context)

        // Then
        assertTrue(result.isLeft())
        assertIs<MfaVerificationError.Expired>(result.leftOrNull())
    }

    @Test
    fun `execute should return InvalidCode and increment attempts for invalid code`() {
        // Given
        val challenge = createValidChallenge()
        val user = createUserWithTotp()

        every { mfaChallengeRepository.findByToken(testMfaToken) } returns challenge
        every { userRepository.findById(testUserId) } returns Optional.of(user)
        every { usedTotpCodeRepository.existsByUserIdAndCodeHashAndTimeStep(any(), any(), any()) } returns false
        every { mfaChallengeRepository.save(any()) } answers { firstArg() }

        val request = createVerificationRequest(code = "000000") // Invalid code
        val context = createContext()

        // When
        val result = verifyMfaUseCase.execute(request, context)

        // Then
        assertTrue(result.isLeft())
        val error = result.leftOrNull()
        assertIs<MfaVerificationError.InvalidCode>(error)
        assertEquals(2, error.remainingAttempts) // 3 max - 1 attempt = 2 remaining

        // Verify challenge was saved with incremented attempts
        val savedChallenge = slot<MfaChallenge>()
        verify { mfaChallengeRepository.save(capture(savedChallenge)) }
        assertEquals(1, savedChallenge.captured.attempts)

        // Verify failed event was published
        verify(exactly = 1) { eventStoreRepository.append(any<MFAVerificationFailed>()) }
    }

    @Test
    fun `execute should return CodeAlreadyUsed when code was already used`() {
        // Given
        val challenge = createValidChallenge()
        val user = createUserWithTotp()
        val validCode = totpService.generateCode(testTotpSecret)

        every { mfaChallengeRepository.findByToken(testMfaToken) } returns challenge
        every { userRepository.findById(testUserId) } returns Optional.of(user)
        every { usedTotpCodeRepository.existsByUserIdAndCodeHashAndTimeStep(any(), any(), any()) } returns true
        every { mfaChallengeRepository.save(any()) } answers { firstArg() }

        val request = createVerificationRequest(code = validCode)
        val context = createContext()

        // When
        val result = verifyMfaUseCase.execute(request, context)

        // Then
        assertTrue(result.isLeft())
        val error = result.leftOrNull()
        assertIs<MfaVerificationError.CodeAlreadyUsed>(error)
        assertEquals(2, error.remainingAttempts)
    }

    @Test
    fun `execute should return Expired after final failed attempt`() {
        // Given
        val challengeWithTwoAttempts = MfaChallenge(
            id = UUID.randomUUID(),
            userId = testUserId,
            token = testMfaToken,
            method = MfaMethod.TOTP,
            expiresAt = Instant.now().plusSeconds(300),
            attempts = 2, // One more attempt allowed
            maxAttempts = 3
        )
        val user = createUserWithTotp()

        every { mfaChallengeRepository.findByToken(testMfaToken) } returns challengeWithTwoAttempts
        every { userRepository.findById(testUserId) } returns Optional.of(user)
        every { usedTotpCodeRepository.existsByUserIdAndCodeHashAndTimeStep(any(), any(), any()) } returns false
        every { mfaChallengeRepository.save(any()) } answers { firstArg() }
        every { mfaChallengeRepository.delete(any()) } just Runs

        val request = createVerificationRequest(code = "000000") // Invalid code
        val context = createContext()

        // When
        val result = verifyMfaUseCase.execute(request, context)

        // Then
        assertTrue(result.isLeft())
        assertIs<MfaVerificationError.Expired>(result.leftOrNull())

        // Verify challenge was deleted after final attempt
        verify(exactly = 1) { mfaChallengeRepository.delete(any()) }
    }

    @Test
    fun `execute should remember device when requested`() {
        // Given
        val challenge = createValidChallenge()
        val user = createUserWithTotp()
        val validCode = totpService.generateCode(testTotpSecret)

        every { mfaChallengeRepository.findByToken(testMfaToken) } returns challenge
        every { userRepository.findById(testUserId) } returns Optional.of(user)
        every { usedTotpCodeRepository.existsByUserIdAndCodeHashAndTimeStep(any(), any(), any()) } returns false
        every { usedTotpCodeRepository.save(any()) } answers { firstArg() }
        every { mfaChallengeRepository.delete(challenge) } just Runs

        val request = createVerificationRequest(code = validCode, rememberDevice = true)
        val context = createContext()

        // When
        val result = verifyMfaUseCase.execute(request, context)

        // Then
        assertTrue(result.isRight())
        val success = result.getOrElse { fail("Expected success") }
        assertEquals(true, success.deviceRemembered)
    }

    @Test
    fun `createChallenge should create and persist new challenge`() {
        // Given
        every { mfaChallengeRepository.deleteByUserId(testUserId) } just Runs
        every { mfaChallengeRepository.save(any()) } answers { firstArg() }

        val correlationId = UUID.randomUUID()

        // When
        val challenge = verifyMfaUseCase.createChallenge(testUserId, MfaMethod.TOTP, correlationId)

        // Then
        assertEquals(testUserId, challenge.userId)
        assertEquals(MfaMethod.TOTP, challenge.method)
        assertEquals(0, challenge.attempts)
        assertTrue(challenge.token.startsWith("mfa_"))
        assertTrue(challenge.expiresAt.isAfter(Instant.now()))

        // Verify old challenges were deleted
        verify(exactly = 1) { mfaChallengeRepository.deleteByUserId(testUserId) }

        // Verify new challenge was saved
        verify(exactly = 1) { mfaChallengeRepository.save(any()) }

        // Verify event was published
        verify(exactly = 1) { eventStoreRepository.append(any<MFAChallengeInitiated>()) }
        verify(exactly = 1) { userEventPublisher.publishMFAChallengeInitiated(any()) }
    }

    @Test
    fun `execute should record metrics for successful verification`() {
        // Given
        val challenge = createValidChallenge()
        val user = createUserWithTotp()
        val validCode = totpService.generateCode(testTotpSecret)

        every { mfaChallengeRepository.findByToken(testMfaToken) } returns challenge
        every { userRepository.findById(testUserId) } returns Optional.of(user)
        every { usedTotpCodeRepository.existsByUserIdAndCodeHashAndTimeStep(any(), any(), any()) } returns false
        every { usedTotpCodeRepository.save(any()) } answers { firstArg() }
        every { mfaChallengeRepository.delete(challenge) } just Runs

        val request = createVerificationRequest(code = validCode)
        val context = createContext()

        // When
        verifyMfaUseCase.execute(request, context)

        // Then
        val successCounter = meterRegistry.counter("mfa_verification_attempts_total", "status", "success")
        assertEquals(1.0, successCounter.count())
    }

    // Helper methods

    private fun createValidChallenge(): MfaChallenge {
        return MfaChallenge(
            id = UUID.randomUUID(),
            userId = testUserId,
            token = testMfaToken,
            method = MfaMethod.TOTP,
            expiresAt = Instant.now().plusSeconds(300), // 5 minutes from now
            attempts = 0,
            maxAttempts = 3
        )
    }

    private fun createUserWithTotp(): User {
        return User(
            id = testUserId,
            email = "test@example.com",
            passwordHash = "hash",
            firstName = "Test",
            lastName = "User",
            status = UserStatus.ACTIVE,
            tosAcceptedAt = Instant.now(),
            marketingOptIn = false,
            registrationSource = RegistrationSource.WEB,
            emailVerified = true,
            verifiedAt = Instant.now(),
            mfaEnabled = true,
            totpSecret = testTotpSecret,
            totpEnabled = true
        )
    }

    private fun createVerificationRequest(
        code: String = "123456",
        rememberDevice: Boolean = false
    ): MfaVerificationRequest {
        return MfaVerificationRequest(
            mfaToken = testMfaToken,
            code = code,
            method = MfaMethod.TOTP,
            rememberDevice = rememberDevice
        )
    }

    private fun createContext(): MfaVerificationContext {
        return MfaVerificationContext(
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            correlationId = UUID.randomUUID()
        )
    }
}
