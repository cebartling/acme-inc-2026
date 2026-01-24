package com.acme.identity.application

import arrow.core.getOrElse
import com.acme.identity.api.v1.dto.RegisterUserRequest
import com.acme.identity.domain.RegistrationSource
import com.acme.identity.domain.User
import com.acme.identity.domain.UserStatus
import com.acme.identity.domain.VerificationToken
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.EventStoreRepository
import com.acme.identity.infrastructure.persistence.UserRepository
import com.acme.identity.infrastructure.persistence.VerificationTokenRepository
import com.acme.identity.infrastructure.security.PasswordHasher
import com.acme.identity.infrastructure.security.UserIdGenerator
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class RegisterUserUseCaseTest {

    private lateinit var userRepository: UserRepository
    private lateinit var verificationTokenRepository: VerificationTokenRepository
    private lateinit var eventStoreRepository: EventStoreRepository
    private lateinit var userEventPublisher: UserEventPublisher
    private lateinit var passwordHasher: PasswordHasher
    private lateinit var userIdGenerator: UserIdGenerator
    private lateinit var verificationTokenGenerator: VerificationTokenGenerator
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var registerUserUseCase: RegisterUserUseCase

    private val testUserId = UUID.randomUUID()
    private val testPasswordHash = "\$argon2id\$v=19\$m=65536,t=3,p=4\$somesalt\$somehash"
    private val testVerificationToken = "test-token-abc123"

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        verificationTokenRepository = mockk()
        eventStoreRepository = mockk()
        userEventPublisher = mockk()
        passwordHasher = mockk()
        userIdGenerator = mockk()
        verificationTokenGenerator = mockk()
        meterRegistry = SimpleMeterRegistry()

        registerUserUseCase = RegisterUserUseCase(
            userRepository = userRepository,
            verificationTokenRepository = verificationTokenRepository,
            eventStoreRepository = eventStoreRepository,
            userEventPublisher = userEventPublisher,
            passwordHasher = passwordHasher,
            userIdGenerator = userIdGenerator,
            verificationTokenGenerator = verificationTokenGenerator,
            meterRegistry = meterRegistry
        )
    }

    @Test
    fun `execute should successfully register new user`() {
        // Given
        val request = createValidRequest()
        val expiration = Instant.now().plusSeconds(86400)

        every { userRepository.existsByEmail(request.email.lowercase()) } returns false
        every { userIdGenerator.generateRaw() } returns testUserId
        every { passwordHasher.hash(request.password) } returns testPasswordHash
        every { verificationTokenGenerator.generate() } returns testVerificationToken
        every { verificationTokenGenerator.calculateExpiration() } returns expiration
        every { eventStoreRepository.append(any()) } just Runs
        every { userRepository.save(any()) } answers { firstArg() }
        every { verificationTokenRepository.save(any()) } answers { firstArg() }
        every { userEventPublisher.publish(any<com.acme.identity.domain.events.UserRegistered>()) } returns CompletableFuture.completedFuture(null as Void?)

        // When
        val result = registerUserUseCase.execute(request)

        // Then
        assertTrue(result.isRight())
        val response = result.getOrElse { fail("Expected success but got error") }
        assertEquals(testUserId, response.userId)
        assertEquals(request.email.lowercase(), response.email)
        assertEquals(UserStatus.PENDING_VERIFICATION, response.status)
        assertNotNull(response.createdAt)

        verify(exactly = 1) { eventStoreRepository.append(any()) }
        verify(exactly = 1) { userRepository.save(any()) }
        verify(exactly = 1) { verificationTokenRepository.save(any()) }
        verify(exactly = 1) { userEventPublisher.publish(any<com.acme.identity.domain.events.UserRegistered>()) }
    }

    @Test
    fun `execute should return DuplicateEmail when email already exists`() {
        // Given
        val request = createValidRequest()

        every { userRepository.existsByEmail(request.email.lowercase()) } returns true

        // When
        val result = registerUserUseCase.execute(request)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertIs<RegistrationError.DuplicateEmail>(error)
                assertEquals(request.email.lowercase(), error.email)
            },
            ifRight = { fail("Expected error but got success") }
        )

        verify(exactly = 0) { userRepository.save(any()) }
        verify(exactly = 0) { eventStoreRepository.append(any()) }
    }

    @Test
    fun `execute should hash password using Argon2id`() {
        // Given
        val request = createValidRequest()
        val expiration = Instant.now().plusSeconds(86400)

        every { userRepository.existsByEmail(any()) } returns false
        every { userIdGenerator.generateRaw() } returns testUserId
        every { passwordHasher.hash(request.password) } returns testPasswordHash
        every { verificationTokenGenerator.generate() } returns testVerificationToken
        every { verificationTokenGenerator.calculateExpiration() } returns expiration
        every { eventStoreRepository.append(any()) } just Runs
        every { userRepository.save(any()) } answers { firstArg() }
        every { verificationTokenRepository.save(any()) } answers { firstArg() }
        every { userEventPublisher.publish(any<com.acme.identity.domain.events.UserRegistered>()) } returns CompletableFuture.completedFuture(null as Void?)

        // When
        registerUserUseCase.execute(request)

        // Then
        verify(exactly = 1) { passwordHasher.hash(request.password) }

        val savedUser = slot<User>()
        verify { userRepository.save(capture(savedUser)) }
        assertEquals(testPasswordHash, savedUser.captured.passwordHash)
    }

    @Test
    fun `execute should generate UUID v7 for user ID`() {
        // Given
        val request = createValidRequest()
        val expiration = Instant.now().plusSeconds(86400)

        every { userRepository.existsByEmail(any()) } returns false
        every { userIdGenerator.generateRaw() } returns testUserId
        every { passwordHasher.hash(any()) } returns testPasswordHash
        every { verificationTokenGenerator.generate() } returns testVerificationToken
        every { verificationTokenGenerator.calculateExpiration() } returns expiration
        every { eventStoreRepository.append(any()) } just Runs
        every { userRepository.save(any()) } answers { firstArg() }
        every { verificationTokenRepository.save(any()) } answers { firstArg() }
        every { userEventPublisher.publish(any<com.acme.identity.domain.events.UserRegistered>()) } returns CompletableFuture.completedFuture(null as Void?)

        // When
        val result = registerUserUseCase.execute(request)

        // Then
        verify(exactly = 1) { userIdGenerator.generateRaw() }
        assertTrue(result.isRight())
        val response = result.getOrElse { fail("Expected success") }
        assertEquals(testUserId, response.userId)
    }

    @Test
    fun `execute should persist event to event store before response`() {
        // Given
        val request = createValidRequest()
        val expiration = Instant.now().plusSeconds(86400)
        val callOrder = mutableListOf<String>()

        every { userRepository.existsByEmail(any()) } returns false
        every { userIdGenerator.generateRaw() } returns testUserId
        every { passwordHasher.hash(any()) } returns testPasswordHash
        every { verificationTokenGenerator.generate() } returns testVerificationToken
        every { verificationTokenGenerator.calculateExpiration() } returns expiration
        every { eventStoreRepository.append(any()) } answers { callOrder.add("eventStore") }
        every { userRepository.save(any()) } answers { callOrder.add("userRepo"); firstArg() }
        every { verificationTokenRepository.save(any()) } answers { firstArg() }
        every { userEventPublisher.publish(any<com.acme.identity.domain.events.UserRegistered>()) } returns CompletableFuture.completedFuture(null as Void?)

        // When
        registerUserUseCase.execute(request)

        // Then - event store should be called before user repo
        assertEquals("eventStore", callOrder[0])
        assertEquals("userRepo", callOrder[1])
    }

    @Test
    fun `execute should generate verification token with 24 hour expiration`() {
        // Given
        val request = createValidRequest()
        val expiration = Instant.now().plusSeconds(86400)

        every { userRepository.existsByEmail(any()) } returns false
        every { userIdGenerator.generateRaw() } returns testUserId
        every { passwordHasher.hash(any()) } returns testPasswordHash
        every { verificationTokenGenerator.generate() } returns testVerificationToken
        every { verificationTokenGenerator.calculateExpiration() } returns expiration
        every { eventStoreRepository.append(any()) } just Runs
        every { userRepository.save(any()) } answers { firstArg() }
        every { verificationTokenRepository.save(any()) } answers { firstArg() }
        every { userEventPublisher.publish(any<com.acme.identity.domain.events.UserRegistered>()) } returns CompletableFuture.completedFuture(null as Void?)

        // When
        registerUserUseCase.execute(request)

        // Then
        val savedToken = slot<VerificationToken>()
        verify { verificationTokenRepository.save(capture(savedToken)) }
        assertEquals(testVerificationToken, savedToken.captured.token)
        assertEquals(expiration, savedToken.captured.expiresAt)
    }

    @Test
    fun `execute should track registration source in event`() {
        // Given
        val request = createValidRequest()
        val expiration = Instant.now().plusSeconds(86400)

        every { userRepository.existsByEmail(any()) } returns false
        every { userIdGenerator.generateRaw() } returns testUserId
        every { passwordHasher.hash(any()) } returns testPasswordHash
        every { verificationTokenGenerator.generate() } returns testVerificationToken
        every { verificationTokenGenerator.calculateExpiration() } returns expiration
        every { eventStoreRepository.append(any()) } just Runs
        every { userRepository.save(any()) } answers { firstArg() }
        every { verificationTokenRepository.save(any()) } answers { firstArg() }
        every { userEventPublisher.publish(any<com.acme.identity.domain.events.UserRegistered>()) } returns CompletableFuture.completedFuture(null as Void?)

        // When
        registerUserUseCase.execute(request, RegistrationSource.MOBILE)

        // Then
        val savedUser = slot<User>()
        verify { userRepository.save(capture(savedUser)) }
        assertEquals(RegistrationSource.MOBILE, savedUser.captured.registrationSource)
    }

    @Test
    fun `execute should record TOS acceptance timestamp`() {
        // Given
        val tosAcceptedAt = Instant.parse("2026-01-02T10:30:00Z")
        val request = createValidRequest().copy(tosAcceptedAt = tosAcceptedAt)
        val expiration = Instant.now().plusSeconds(86400)

        every { userRepository.existsByEmail(any()) } returns false
        every { userIdGenerator.generateRaw() } returns testUserId
        every { passwordHasher.hash(any()) } returns testPasswordHash
        every { verificationTokenGenerator.generate() } returns testVerificationToken
        every { verificationTokenGenerator.calculateExpiration() } returns expiration
        every { eventStoreRepository.append(any()) } just Runs
        every { userRepository.save(any()) } answers { firstArg() }
        every { verificationTokenRepository.save(any()) } answers { firstArg() }
        every { userEventPublisher.publish(any<com.acme.identity.domain.events.UserRegistered>()) } returns CompletableFuture.completedFuture(null as Void?)

        // When
        registerUserUseCase.execute(request)

        // Then
        val savedUser = slot<User>()
        verify { userRepository.save(capture(savedUser)) }
        assertEquals(tosAcceptedAt, savedUser.captured.tosAcceptedAt)
    }

    private fun createValidRequest(): RegisterUserRequest {
        return RegisterUserRequest(
            email = "customer@example.com",
            password = "SecureP@ss123",
            firstName = "Jane",
            lastName = "Doe",
            tosAccepted = true,
            tosAcceptedAt = Instant.now(),
            marketingOptIn = false
        )
    }
}
