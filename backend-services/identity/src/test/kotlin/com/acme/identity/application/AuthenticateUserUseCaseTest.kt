package com.acme.identity.application

import arrow.core.getOrElse
import com.acme.identity.api.v1.dto.SigninRequest
import com.acme.identity.api.v1.dto.SigninStatus
import com.acme.identity.domain.RegistrationSource
import com.acme.identity.domain.User
import com.acme.identity.domain.UserStatus
import com.acme.identity.domain.events.AccountLocked
import com.acme.identity.domain.events.AccountUnlocked
import com.acme.identity.domain.events.AuthenticationFailed
import com.acme.identity.domain.events.AuthenticationSucceeded
import com.acme.identity.domain.events.DomainEvent
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.EventStoreRepository
import com.acme.identity.infrastructure.persistence.UserRepository
import com.acme.identity.infrastructure.security.PasswordHasher
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class AuthenticateUserUseCaseTest {

    private lateinit var userRepository: UserRepository
    private lateinit var eventStoreRepository: EventStoreRepository
    private lateinit var userEventPublisher: UserEventPublisher
    private lateinit var passwordHasher: PasswordHasher
    private lateinit var mfaChallengeService: MfaChallengeService
    private lateinit var smsMfaService: SmsMfaService
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var authenticateUserUseCase: AuthenticateUserUseCase

    private val testUserId = UUID.randomUUID()
    private val testPasswordHash = "\$argon2id\$v=19\$m=65536,t=3,p=4\$somesalt\$somehash"
    private val testEmail = "customer@example.com"
    private val testPassword = "SecureP@ss123"

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        eventStoreRepository = mockk()
        userEventPublisher = mockk()
        passwordHasher = mockk()
        mfaChallengeService = mockk()
        smsMfaService = mockk()
        meterRegistry = SimpleMeterRegistry()

        authenticateUserUseCase = AuthenticateUserUseCase(
            userRepository = userRepository,
            eventStoreRepository = eventStoreRepository,
            userEventPublisher = userEventPublisher,
            passwordHasher = passwordHasher,
            mfaChallengeService = mfaChallengeService,
            smsMfaService = smsMfaService,
            meterRegistry = meterRegistry,
            maxFailedAttempts = 5,
            lockoutDurationMinutes = 15,
            supportUrl = "https://www.acme.com/support",
            passwordResetUrl = "https://www.acme.com/forgot-password"
        )

        // Default mock for password hashing (for dummy password check)
        every { passwordHasher.hash(any()) } returns testPasswordHash
    }

    @Test
    fun `execute should successfully authenticate user with valid credentials`() {
        // Given
        val request = createValidRequest()
        val context = createContext()
        val user = createActiveUser()

        every { userRepository.findByEmail(testEmail) } returns user
        every { passwordHasher.verify(testPassword, user.passwordHash) } returns true
        every { userRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishAuthenticationSucceeded(any()) } returns CompletableFuture.completedFuture(null)

        // When
        val result = authenticateUserUseCase.execute(request, context)

        // Then
        assertTrue(result.isRight())
        val response = result.getOrElse { fail("Expected success but got error") }
        assertEquals(SigninStatus.SUCCESS, response.status)
        assertEquals(testUserId, response.userId)
        assertNotNull(response.expiresIn)

        // Verify user was updated (failed attempts reset, last login updated)
        val savedUser = slot<User>()
        verify { userRepository.save(capture(savedUser)) }
        assertEquals(0, savedUser.captured.failedAttempts)
        assertNotNull(savedUser.captured.lastLoginAt)

        // Verify success event was published
        verify(exactly = 1) { eventStoreRepository.append(any<AuthenticationSucceeded>()) }
        verify(exactly = 1) { userEventPublisher.publishAuthenticationSucceeded(any()) }
    }

    @Test
    fun `execute should return InvalidCredentials for non-existent user`() {
        // Given
        val request = createValidRequest()
        val context = createContext()

        every { userRepository.findByEmail(testEmail) } returns null
        every { passwordHasher.verify(any(), any()) } returns false
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishAuthenticationFailed(any()) } returns CompletableFuture.completedFuture(null)

        // When
        val result = authenticateUserUseCase.execute(request, context)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertIs<AuthenticationError.InvalidCredentials>(error)
                assertEquals(0, error.remainingAttempts)
            },
            ifRight = { fail("Expected error but got success") }
        )

        // Verify failed event was published
        verify(exactly = 1) { eventStoreRepository.append(any<AuthenticationFailed>()) }
        verify(exactly = 1) { userEventPublisher.publishAuthenticationFailed(any()) }

        // Verify dummy password check was performed (timing attack prevention)
        verify(exactly = 1) { passwordHasher.verify(any(), any()) }
    }

    @Test
    fun `execute should return InvalidCredentials for wrong password`() {
        // Given
        val request = createValidRequest()
        val context = createContext()
        val user = createActiveUser()

        every { userRepository.findByEmail(testEmail) } returns user
        every { passwordHasher.verify(testPassword, user.passwordHash) } returns false
        every { userRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishAuthenticationFailed(any()) } returns CompletableFuture.completedFuture(null)

        // When
        val result = authenticateUserUseCase.execute(request, context)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertIs<AuthenticationError.InvalidCredentials>(error)
                assertEquals(4, error.remainingAttempts) // 5 max - 1 failed = 4 remaining
            },
            ifRight = { fail("Expected error but got success") }
        )

        // Verify failed attempts was incremented
        val savedUser = slot<User>()
        verify { userRepository.save(capture(savedUser)) }
        assertEquals(1, savedUser.captured.failedAttempts)
    }

    @Test
    fun `execute should increment failed attempts on each failed attempt`() {
        // Given
        val request = createValidRequest()
        val context = createContext()
        val user = createActiveUser().apply { failedAttempts = 3 }

        every { userRepository.findByEmail(testEmail) } returns user
        every { passwordHasher.verify(testPassword, user.passwordHash) } returns false
        every { userRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishAuthenticationFailed(any()) } returns CompletableFuture.completedFuture(null)

        // When
        val result = authenticateUserUseCase.execute(request, context)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertIs<AuthenticationError.InvalidCredentials>(error)
                assertEquals(1, error.remainingAttempts) // 5 max - 4 failed = 1 remaining
            },
            ifRight = { fail("Expected error but got success") }
        )

        val savedUser = slot<User>()
        verify { userRepository.save(capture(savedUser)) }
        assertEquals(4, savedUser.captured.failedAttempts)
    }

    @Test
    fun `execute should reset failed attempts on successful authentication`() {
        // Given
        val request = createValidRequest()
        val context = createContext()
        val user = createActiveUser().apply { failedAttempts = 3 }

        every { userRepository.findByEmail(testEmail) } returns user
        every { passwordHasher.verify(testPassword, user.passwordHash) } returns true
        every { userRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishAuthenticationSucceeded(any()) } returns CompletableFuture.completedFuture(null)

        // When
        val result = authenticateUserUseCase.execute(request, context)

        // Then
        assertTrue(result.isRight())

        val savedUser = slot<User>()
        verify { userRepository.save(capture(savedUser)) }
        assertEquals(0, savedUser.captured.failedAttempts)
    }

    @Test
    fun `execute should return AccountInactive for PENDING_VERIFICATION status`() {
        // Given
        val request = createValidRequest()
        val context = createContext()
        val user = createUser(status = UserStatus.PENDING_VERIFICATION)

        every { userRepository.findByEmail(testEmail) } returns user
        every { passwordHasher.verify(testPassword, user.passwordHash) } returns true
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishAuthenticationFailed(any()) } returns CompletableFuture.completedFuture(null)

        // When
        val result = authenticateUserUseCase.execute(request, context)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertIs<AuthenticationError.AccountInactive>(error)
                assertEquals(UserStatus.PENDING_VERIFICATION, error.status)
            },
            ifRight = { fail("Expected error but got success") }
        )
    }

    @Test
    fun `execute should return AccountInactive for SUSPENDED status`() {
        // Given
        val request = createValidRequest()
        val context = createContext()
        val user = createUser(status = UserStatus.SUSPENDED)

        every { userRepository.findByEmail(testEmail) } returns user
        every { passwordHasher.verify(testPassword, user.passwordHash) } returns true
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishAuthenticationFailed(any()) } returns CompletableFuture.completedFuture(null)

        // When
        val result = authenticateUserUseCase.execute(request, context)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertIs<AuthenticationError.AccountInactive>(error)
                assertEquals(UserStatus.SUSPENDED, error.status)
            },
            ifRight = { fail("Expected error but got success") }
        )
    }

    @Test
    fun `execute should return AccountInactive for DEACTIVATED status`() {
        // Given
        val request = createValidRequest()
        val context = createContext()
        val user = createUser(status = UserStatus.DEACTIVATED)

        every { userRepository.findByEmail(testEmail) } returns user
        every { passwordHasher.verify(testPassword, user.passwordHash) } returns true
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishAuthenticationFailed(any()) } returns CompletableFuture.completedFuture(null)

        // When
        val result = authenticateUserUseCase.execute(request, context)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertIs<AuthenticationError.AccountInactive>(error)
                assertEquals(UserStatus.DEACTIVATED, error.status)
            },
            ifRight = { fail("Expected error but got success") }
        )
    }

    @Test
    fun `execute should return AccountLocked for locked user`() {
        // Given
        val request = createValidRequest()
        val context = createContext()
        val user = createUser(status = UserStatus.LOCKED).apply {
            lockedUntil = Instant.now().plusSeconds(3600) // Locked for 1 hour
        }

        every { userRepository.findByEmail(testEmail) } returns user
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishAuthenticationFailed(any()) } returns CompletableFuture.completedFuture(null)

        // When
        val result = authenticateUserUseCase.execute(request, context)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertIs<AuthenticationError.AccountLocked>(error)
                assertNotNull(error.lockedUntil)
            },
            ifRight = { fail("Expected error but got success") }
        )
    }

    // =========================================================================
    // Lockout Expiration Tests (US-0003-04)
    // =========================================================================

    @Test
    fun `execute should detect expired lockout and unlock account`() {
        // Given
        val request = createValidRequest()
        val context = createContext()
        val user = createUser(status = UserStatus.LOCKED).apply {
            lockedUntil = Instant.now().minusSeconds(60) // Expired 1 minute ago
            failedAttempts = 5
        }

        every { userRepository.findByEmail(testEmail) } returns user
        every { passwordHasher.verify(testPassword, user.passwordHash) } returns true
        every { userRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishAccountUnlocked(any()) } returns CompletableFuture.completedFuture(null)
        every { userEventPublisher.publishAuthenticationSucceeded(any()) } returns CompletableFuture.completedFuture(null)

        // When
        val result = authenticateUserUseCase.execute(request, context)

        // Then
        assertTrue(result.isRight())
        val response = result.getOrElse { fail("Expected success but got error") }
        assertEquals(SigninStatus.SUCCESS, response.status)
    }

    @Test
    fun `execute should save unlocked account after lockout expiration`() {
        // Given
        val request = createValidRequest()
        val context = createContext()
        val user = createUser(status = UserStatus.LOCKED).apply {
            lockedUntil = Instant.now().minusSeconds(60) // Expired 1 minute ago
            failedAttempts = 5
        }

        every { userRepository.findByEmail(testEmail) } returns user
        every { passwordHasher.verify(testPassword, user.passwordHash) } returns true
        every { userRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishAccountUnlocked(any()) } returns CompletableFuture.completedFuture(null)
        every { userEventPublisher.publishAuthenticationSucceeded(any()) } returns CompletableFuture.completedFuture(null)

        // When
        authenticateUserUseCase.execute(request, context)

        // Then - verify user was saved with unlocked status
        val savedUsers = mutableListOf<User>()
        verify(atLeast = 1) { userRepository.save(capture(savedUsers)) }

        // First save should be the unlock
        val unlockedUser = savedUsers.first()
        assertEquals(UserStatus.ACTIVE, unlockedUser.status)
        assertEquals(null, unlockedUser.lockedUntil)
    }

    @Test
    fun `execute should publish AccountUnlocked event with LOCKOUT_EXPIRED reason`() {
        // Given
        val request = createValidRequest()
        val context = createContext()
        val user = createUser(status = UserStatus.LOCKED).apply {
            lockedUntil = Instant.now().minusSeconds(60) // Expired 1 minute ago
            failedAttempts = 5
        }

        val capturedEvents = mutableListOf<DomainEvent>()
        every { userRepository.findByEmail(testEmail) } returns user
        every { passwordHasher.verify(testPassword, user.passwordHash) } returns true
        every { userRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(capture(capturedEvents)) } just Runs
        every { userEventPublisher.publishAccountUnlocked(any()) } returns CompletableFuture.completedFuture(null)
        every { userEventPublisher.publishAuthenticationSucceeded(any()) } returns CompletableFuture.completedFuture(null)

        // When
        authenticateUserUseCase.execute(request, context)

        // Then - verify AccountUnlocked event was published (capture and filter by type)
        val accountUnlockedEvents = capturedEvents.filterIsInstance<AccountUnlocked>()
        assertEquals(1, accountUnlockedEvents.size)
        verify(exactly = 1) { userEventPublisher.publishAccountUnlocked(any()) }
    }

    @Test
    fun `execute should allow successful signin after lockout expiration`() {
        // Given
        val request = createValidRequest()
        val context = createContext()
        val user = createUser(status = UserStatus.LOCKED).apply {
            lockedUntil = Instant.now().minusSeconds(60) // Expired 1 minute ago
            failedAttempts = 5
        }

        every { userRepository.findByEmail(testEmail) } returns user
        every { passwordHasher.verify(testPassword, user.passwordHash) } returns true
        every { userRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishAccountUnlocked(any()) } returns CompletableFuture.completedFuture(null)
        every { userEventPublisher.publishAuthenticationSucceeded(any()) } returns CompletableFuture.completedFuture(null)

        // When
        val result = authenticateUserUseCase.execute(request, context)

        // Then
        assertTrue(result.isRight())
        val response = result.getOrElse { fail("Expected success but got error") }
        assertEquals(SigninStatus.SUCCESS, response.status)
        assertEquals(testUserId, response.userId)

        // Verify success event was also published
        verify(exactly = 1) { userEventPublisher.publishAuthenticationSucceeded(any()) }
    }

    @Test
    fun `execute should record lockout_expired metric when lockout expires`() {
        // Given
        val request = createValidRequest()
        val context = createContext()
        val user = createUser(status = UserStatus.LOCKED).apply {
            lockedUntil = Instant.now().minusSeconds(60) // Expired 1 minute ago
            failedAttempts = 5
        }

        every { userRepository.findByEmail(testEmail) } returns user
        every { passwordHasher.verify(testPassword, user.passwordHash) } returns true
        every { userRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishAccountUnlocked(any()) } returns CompletableFuture.completedFuture(null)
        every { userEventPublisher.publishAuthenticationSucceeded(any()) } returns CompletableFuture.completedFuture(null)

        // When
        authenticateUserUseCase.execute(request, context)

        // Then
        val counter = meterRegistry.counter("authentication_attempts_total", "status", "lockout_expired")
        assertEquals(1.0, counter.count())
    }

    // =========================================================================
    // Lockout Triggering Tests (US-0003-04)
    // =========================================================================

    @Test
    fun `execute should lock account after 5 failed attempts`() {
        // Given
        val request = createValidRequest()
        val context = createContext()
        val user = createActiveUser().apply { failedAttempts = 4 } // One more failure triggers lockout

        every { userRepository.findByEmail(testEmail) } returns user
        every { passwordHasher.verify(testPassword, user.passwordHash) } returns false
        every { userRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishAuthenticationFailed(any()) } returns CompletableFuture.completedFuture(null)
        every { userEventPublisher.publishAccountLocked(any()) } returns CompletableFuture.completedFuture(null)

        // When
        val result = authenticateUserUseCase.execute(request, context)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertIs<AuthenticationError.AccountLocked>(error)
                assertNotNull(error.lockedUntil)
                assertTrue(error.lockoutRemainingSeconds > 0)
            },
            ifRight = { fail("Expected error but got success") }
        )

        // Verify user was locked
        val savedUser = slot<User>()
        verify { userRepository.save(capture(savedUser)) }
        assertEquals(UserStatus.LOCKED, savedUser.captured.status)
        assertNotNull(savedUser.captured.lockedUntil)
    }

    @Test
    fun `execute should NOT lock account after 4 failed attempts`() {
        // Given - user has 3 previous failed attempts, this will be the 4th
        val request = createValidRequest()
        val context = createContext()
        val user = createActiveUser().apply { failedAttempts = 3 }

        every { userRepository.findByEmail(testEmail) } returns user
        every { passwordHasher.verify(testPassword, user.passwordHash) } returns false
        every { userRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishAuthenticationFailed(any()) } returns CompletableFuture.completedFuture(null)

        // When
        val result = authenticateUserUseCase.execute(request, context)

        // Then - should return InvalidCredentials, NOT AccountLocked
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertIs<AuthenticationError.InvalidCredentials>(error)
                assertEquals(1, error.remainingAttempts) // 5 max - 4 failed = 1 remaining
            },
            ifRight = { fail("Expected error but got success") }
        )

        // Verify user was NOT locked (status should still be ACTIVE)
        val savedUser = slot<User>()
        verify { userRepository.save(capture(savedUser)) }
        assertEquals(UserStatus.ACTIVE, savedUser.captured.status)
        assertEquals(null, savedUser.captured.lockedUntil)
        assertEquals(4, savedUser.captured.failedAttempts)

        // Verify AccountLocked event was NOT published
        verify(exactly = 0) { userEventPublisher.publishAccountLocked(any()) }
    }

    @Test
    fun `execute should publish AccountLocked event when lockout triggers`() {
        // Given
        val request = createValidRequest()
        val context = createContext()
        val user = createActiveUser().apply { failedAttempts = 4 } // One more failure triggers lockout

        val capturedEvents = mutableListOf<DomainEvent>()
        every { userRepository.findByEmail(testEmail) } returns user
        every { passwordHasher.verify(testPassword, user.passwordHash) } returns false
        every { userRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(capture(capturedEvents)) } just Runs
        every { userEventPublisher.publishAuthenticationFailed(any()) } returns CompletableFuture.completedFuture(null)
        every { userEventPublisher.publishAccountLocked(any()) } returns CompletableFuture.completedFuture(null)

        // When
        authenticateUserUseCase.execute(request, context)

        // Then - verify AccountLocked event was published (capture and filter by type)
        val accountLockedEvents = capturedEvents.filterIsInstance<AccountLocked>()
        assertEquals(1, accountLockedEvents.size)
        verify(exactly = 1) { userEventPublisher.publishAccountLocked(any()) }
    }

    @Test
    fun `execute should set lockout duration to 15 minutes`() {
        // Given
        val request = createValidRequest()
        val context = createContext()
        val user = createActiveUser().apply { failedAttempts = 4 }

        every { userRepository.findByEmail(testEmail) } returns user
        every { passwordHasher.verify(testPassword, user.passwordHash) } returns false
        every { userRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishAuthenticationFailed(any()) } returns CompletableFuture.completedFuture(null)
        every { userEventPublisher.publishAccountLocked(any()) } returns CompletableFuture.completedFuture(null)

        // When
        val result = authenticateUserUseCase.execute(request, context)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertIs<AuthenticationError.AccountLocked>(error)
                // 15 minutes = 900 seconds
                assertTrue(error.lockoutRemainingSeconds in 895..900)
            },
            ifRight = { fail("Expected error but got success") }
        )
    }

    @Test
    fun `execute should record account_locked_triggered metric when lockout triggers`() {
        // Given
        val request = createValidRequest()
        val context = createContext()
        val user = createActiveUser().apply { failedAttempts = 4 }

        every { userRepository.findByEmail(testEmail) } returns user
        every { passwordHasher.verify(testPassword, user.passwordHash) } returns false
        every { userRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishAuthenticationFailed(any()) } returns CompletableFuture.completedFuture(null)
        every { userEventPublisher.publishAccountLocked(any()) } returns CompletableFuture.completedFuture(null)

        // When
        authenticateUserUseCase.execute(request, context)

        // Then
        val counter = meterRegistry.counter("authentication_attempts_total", "status", "account_locked_triggered")
        assertEquals(1.0, counter.count())
    }

    @Test
    fun `execute should return remaining lockout seconds for locked account`() {
        // Given
        val request = createValidRequest()
        val context = createContext()
        val user = createUser(status = UserStatus.LOCKED).apply {
            lockedUntil = Instant.now().plusSeconds(600) // 10 minutes remaining
        }

        every { userRepository.findByEmail(testEmail) } returns user
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishAuthenticationFailed(any()) } returns CompletableFuture.completedFuture(null)

        // When
        val result = authenticateUserUseCase.execute(request, context)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertIs<AuthenticationError.AccountLocked>(error)
                // Should be approximately 600 seconds (allow some variance for test execution time)
                assertTrue(error.lockoutRemainingSeconds in 595..600)
            },
            ifRight = { fail("Expected error but got success") }
        )
    }

    @Test
    fun `execute should return MFA_REQUIRED for user with MFA enabled`() {
        // Given
        val request = createValidRequest()
        val context = createContext()
        val user = createActiveUser().apply {
            mfaEnabled = true
            totpEnabled = true
            totpSecret = "JBSWY3DPEHPK3PXP"
        }

        every { userRepository.findByEmail(testEmail) } returns user
        every { passwordHasher.verify(testPassword, user.passwordHash) } returns true
        every { userRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishAuthenticationSucceeded(any()) } returns CompletableFuture.completedFuture(null)
        every { mfaChallengeService.createChallenge(any(), any(), any()) } answers {
            com.acme.identity.domain.MfaChallenge.create(
                userId = firstArg(),
                method = secondArg()
            )
        }

        // When
        val result = authenticateUserUseCase.execute(request, context)

        // Then
        assertTrue(result.isRight())
        val response = result.getOrElse { fail("Expected success but got error") }
        assertEquals(SigninStatus.MFA_REQUIRED, response.status)
        assertNotNull(response.mfaToken)
        assertNotNull(response.mfaMethods)
        assertTrue(response.mfaMethods!!.isNotEmpty())
    }

    @Test
    fun `execute should capture device fingerprint on signin`() {
        // Given
        val deviceFingerprint = "fp_abc123"
        val request = createValidRequest().copy(deviceFingerprint = deviceFingerprint)
        val context = createContext()
        val user = createActiveUser()

        every { userRepository.findByEmail(testEmail) } returns user
        every { passwordHasher.verify(testPassword, user.passwordHash) } returns true
        every { userRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishAuthenticationSucceeded(any()) } returns CompletableFuture.completedFuture(null)

        // When
        val result = authenticateUserUseCase.execute(request, context)

        // Then
        assertTrue(result.isRight())

        val savedUser = slot<User>()
        verify { userRepository.save(capture(savedUser)) }
        assertEquals(deviceFingerprint, savedUser.captured.lastDeviceFingerprint)
    }

    @Test
    fun `execute should persist event to event store before returning`() {
        // Given
        val request = createValidRequest()
        val context = createContext()
        val user = createActiveUser()
        val callOrder = mutableListOf<String>()

        every { userRepository.findByEmail(testEmail) } returns user
        every { passwordHasher.verify(testPassword, user.passwordHash) } returns true
        every { userRepository.save(any()) } answers { callOrder.add("userRepo"); firstArg() }
        every { eventStoreRepository.append(any()) } answers { callOrder.add("eventStore") }
        every { userEventPublisher.publishAuthenticationSucceeded(any()) } returns CompletableFuture.completedFuture(null)

        // When
        authenticateUserUseCase.execute(request, context)

        // Then - event store should be called
        assertTrue(callOrder.contains("eventStore"))
        assertTrue(callOrder.contains("userRepo"))
    }

    @Test
    fun `execute should normalize email to lowercase`() {
        // Given
        val request = createValidRequest().copy(email = "Customer@Example.COM")
        val context = createContext()

        every { userRepository.findByEmail("customer@example.com") } returns null
        every { passwordHasher.verify(any(), any()) } returns false
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishAuthenticationFailed(any()) } returns CompletableFuture.completedFuture(null)

        // When
        authenticateUserUseCase.execute(request, context)

        // Then
        verify { userRepository.findByEmail("customer@example.com") }
    }

    @Test
    fun `execute should record metrics for successful authentication`() {
        // Given
        val request = createValidRequest()
        val context = createContext()
        val user = createActiveUser()

        every { userRepository.findByEmail(testEmail) } returns user
        every { passwordHasher.verify(testPassword, user.passwordHash) } returns true
        every { userRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishAuthenticationSucceeded(any()) } returns CompletableFuture.completedFuture(null)

        // When
        authenticateUserUseCase.execute(request, context)

        // Then
        val counter = meterRegistry.counter("authentication_attempts_total", "status", "success")
        assertEquals(1.0, counter.count())
    }

    @Test
    fun `execute should record metrics for failed authentication`() {
        // Given
        val request = createValidRequest()
        val context = createContext()

        every { userRepository.findByEmail(testEmail) } returns null
        every { passwordHasher.verify(any(), any()) } returns false
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishAuthenticationFailed(any()) } returns CompletableFuture.completedFuture(null)

        // When
        authenticateUserUseCase.execute(request, context)

        // Then
        val counter = meterRegistry.counter("authentication_attempts_total", "status", "invalid_credentials")
        assertEquals(1.0, counter.count())
    }

    private fun createValidRequest(): SigninRequest {
        return SigninRequest(
            email = testEmail,
            password = testPassword,
            rememberMe = false,
            deviceFingerprint = null
        )
    }

    private fun createContext(): AuthenticationContext {
        return AuthenticationContext(
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            correlationId = UUID.randomUUID()
        )
    }

    private fun createActiveUser(): User {
        return createUser(status = UserStatus.ACTIVE)
    }

    private fun createUser(status: UserStatus): User {
        return User(
            id = testUserId,
            email = testEmail,
            passwordHash = testPasswordHash,
            firstName = "Jane",
            lastName = "Doe",
            status = status,
            tosAcceptedAt = Instant.now(),
            marketingOptIn = false,
            registrationSource = RegistrationSource.WEB,
            emailVerified = status == UserStatus.ACTIVE,
            verifiedAt = if (status == UserStatus.ACTIVE) Instant.now() else null,
            failedAttempts = 0,
            lockedUntil = null,
            mfaEnabled = false,
            lastLoginAt = null,
            lastDeviceFingerprint = null
        )
    }
}
