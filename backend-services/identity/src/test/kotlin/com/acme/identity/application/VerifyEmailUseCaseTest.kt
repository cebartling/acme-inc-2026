package com.acme.identity.application

import arrow.core.getOrElse
import com.acme.identity.domain.RegistrationSource
import com.acme.identity.domain.User
import com.acme.identity.domain.UserStatus
import com.acme.identity.domain.VerificationToken
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.EventStoreRepository
import com.acme.identity.infrastructure.persistence.UserRepository
import com.acme.identity.infrastructure.persistence.VerificationTokenRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class VerifyEmailUseCaseTest {

    private lateinit var userRepository: UserRepository
    private lateinit var verificationTokenRepository: VerificationTokenRepository
    private lateinit var eventStoreRepository: EventStoreRepository
    private lateinit var userEventPublisher: UserEventPublisher
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var verifyEmailUseCase: VerifyEmailUseCase

    private val testUserId = UUID.randomUUID()
    private val testToken = "valid-token-abc123"

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        verificationTokenRepository = mockk()
        eventStoreRepository = mockk()
        userEventPublisher = mockk()
        meterRegistry = SimpleMeterRegistry()

        verifyEmailUseCase = VerifyEmailUseCase(
            userRepository = userRepository,
            verificationTokenRepository = verificationTokenRepository,
            eventStoreRepository = eventStoreRepository,
            userEventPublisher = userEventPublisher,
            meterRegistry = meterRegistry
        )
    }

    @Test
    fun `execute should successfully verify email and activate user`() {
        // Given
        val verificationToken = createValidToken()
        val user = createPendingUser()

        every { verificationTokenRepository.findByToken(testToken) } returns verificationToken
        every { verificationTokenRepository.save(any()) } answers { firstArg() }
        every { userRepository.findById(testUserId) } returns Optional.of(user)
        every { userRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishEmailVerified(any()) } returns CompletableFuture.completedFuture(null)
        every { userEventPublisher.publishUserActivated(any()) } returns CompletableFuture.completedFuture(null)

        // When
        val result = verifyEmailUseCase.execute(testToken)

        // Then
        assertTrue(result.isRight())
        val success = result.getOrElse { fail("Expected success but got error") }
        assertEquals(testUserId, success.userId)
        assertEquals("customer@example.com", success.email)
        assertNotNull(success.activatedAt)

        verify(exactly = 1) { verificationTokenRepository.save(any()) }
        verify(exactly = 1) { userRepository.save(any()) }
        verify(exactly = 2) { eventStoreRepository.append(any()) }
        verify(exactly = 1) { userEventPublisher.publishEmailVerified(any()) }
        verify(exactly = 1) { userEventPublisher.publishUserActivated(any()) }
    }

    @Test
    fun `execute should return InvalidToken when token does not exist`() {
        // Given
        every { verificationTokenRepository.findByToken(testToken) } returns null

        // When
        val result = verifyEmailUseCase.execute(testToken)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertIs<VerificationError.InvalidToken>(error)
            },
            ifRight = { fail("Expected error but got success") }
        )

        verify(exactly = 0) { userRepository.findById(any()) }
        verify(exactly = 0) { eventStoreRepository.append(any()) }
    }

    @Test
    fun `execute should return ExpiredToken when token is expired`() {
        // Given
        val expiredToken = VerificationToken(
            id = UUID.randomUUID(),
            userId = testUserId,
            token = testToken,
            expiresAt = Instant.now().minusSeconds(3600) // Expired 1 hour ago
        )

        every { verificationTokenRepository.findByToken(testToken) } returns expiredToken

        // When
        val result = verifyEmailUseCase.execute(testToken)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertIs<VerificationError.ExpiredToken>(error)
            },
            ifRight = { fail("Expected error but got success") }
        )

        verify(exactly = 0) { userRepository.findById(any()) }
        verify(exactly = 0) { eventStoreRepository.append(any()) }
    }

    @Test
    fun `execute should return AlreadyVerified when token is already used`() {
        // Given
        val usedToken = VerificationToken(
            id = UUID.randomUUID(),
            userId = testUserId,
            token = testToken,
            expiresAt = Instant.now().plusSeconds(3600),
            usedAt = Instant.now().minusSeconds(1800) // Used 30 minutes ago
        )

        every { verificationTokenRepository.findByToken(testToken) } returns usedToken

        // When
        val result = verifyEmailUseCase.execute(testToken)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertIs<VerificationError.AlreadyVerified>(error)
            },
            ifRight = { fail("Expected error but got success") }
        )

        verify(exactly = 0) { userRepository.findById(any()) }
        verify(exactly = 0) { eventStoreRepository.append(any()) }
    }

    @Test
    fun `execute should return AlreadyVerified when user email is already verified`() {
        // Given
        val verificationToken = createValidToken()
        val verifiedUser = User(
            id = testUserId,
            email = "customer@example.com",
            passwordHash = "hash",
            firstName = "Jane",
            lastName = "Doe",
            status = UserStatus.ACTIVE,
            tosAcceptedAt = Instant.now(),
            registrationSource = RegistrationSource.WEB,
            emailVerified = true,
            verifiedAt = Instant.now().minusSeconds(3600)
        )

        every { verificationTokenRepository.findByToken(testToken) } returns verificationToken
        every { userRepository.findById(testUserId) } returns Optional.of(verifiedUser)
        every { verificationTokenRepository.save(any()) } answers { firstArg() }

        // When
        val result = verifyEmailUseCase.execute(testToken)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertIs<VerificationError.AlreadyVerified>(error)
            },
            ifRight = { fail("Expected error but got success") }
        )

        // Token should still be marked as used
        verify(exactly = 1) { verificationTokenRepository.save(any()) }
        verify(exactly = 0) { eventStoreRepository.append(any()) }
    }

    @Test
    fun `execute should mark token as used`() {
        // Given
        val verificationToken = createValidToken()
        val user = createPendingUser()
        val savedToken = slot<VerificationToken>()

        every { verificationTokenRepository.findByToken(testToken) } returns verificationToken
        every { verificationTokenRepository.save(capture(savedToken)) } answers { firstArg() }
        every { userRepository.findById(testUserId) } returns Optional.of(user)
        every { userRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishEmailVerified(any()) } returns CompletableFuture.completedFuture(null)
        every { userEventPublisher.publishUserActivated(any()) } returns CompletableFuture.completedFuture(null)

        // When
        verifyEmailUseCase.execute(testToken)

        // Then
        assertNotNull(savedToken.captured.usedAt)
    }

    @Test
    fun `execute should activate user and mark email as verified`() {
        // Given
        val verificationToken = createValidToken()
        val user = createPendingUser()
        val savedUser = slot<User>()

        every { verificationTokenRepository.findByToken(testToken) } returns verificationToken
        every { verificationTokenRepository.save(any()) } answers { firstArg() }
        every { userRepository.findById(testUserId) } returns Optional.of(user)
        every { userRepository.save(capture(savedUser)) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishEmailVerified(any()) } returns CompletableFuture.completedFuture(null)
        every { userEventPublisher.publishUserActivated(any()) } returns CompletableFuture.completedFuture(null)

        // When
        verifyEmailUseCase.execute(testToken)

        // Then
        assertEquals(UserStatus.ACTIVE, savedUser.captured.status)
        assertEquals(true, savedUser.captured.emailVerified)
        assertNotNull(savedUser.captured.verifiedAt)
    }

    @Test
    fun `execute should persist events atomically`() {
        // Given
        val verificationToken = createValidToken()
        val user = createPendingUser()
        val eventOrder = mutableListOf<String>()

        every { verificationTokenRepository.findByToken(testToken) } returns verificationToken
        every { verificationTokenRepository.save(any()) } answers { firstArg() }
        every { userRepository.findById(testUserId) } returns Optional.of(user)
        every { userRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } answers {
            val event = firstArg<Any>()
            eventOrder.add(event.javaClass.simpleName)
        }
        every { userEventPublisher.publishEmailVerified(any()) } returns CompletableFuture.completedFuture(null)
        every { userEventPublisher.publishUserActivated(any()) } returns CompletableFuture.completedFuture(null)

        // When
        verifyEmailUseCase.execute(testToken)

        // Then
        assertEquals(2, eventOrder.size)
        assertEquals("EmailVerified", eventOrder[0])
        assertEquals("UserActivated", eventOrder[1])
    }

    @Test
    fun `execute should record metrics on success`() {
        // Given
        val verificationToken = createValidToken()
        val user = createPendingUser()

        every { verificationTokenRepository.findByToken(testToken) } returns verificationToken
        every { verificationTokenRepository.save(any()) } answers { firstArg() }
        every { userRepository.findById(testUserId) } returns Optional.of(user)
        every { userRepository.save(any()) } answers { firstArg() }
        every { eventStoreRepository.append(any()) } just Runs
        every { userEventPublisher.publishEmailVerified(any()) } returns CompletableFuture.completedFuture(null)
        every { userEventPublisher.publishUserActivated(any()) } returns CompletableFuture.completedFuture(null)

        // When
        verifyEmailUseCase.execute(testToken)

        // Then
        val counter = meterRegistry.counter("email_verification_total", "result", "success")
        assertEquals(1.0, counter.count())
    }

    private fun createValidToken(): VerificationToken {
        return VerificationToken(
            id = UUID.randomUUID(),
            userId = testUserId,
            token = testToken,
            expiresAt = Instant.now().plusSeconds(86400) // Valid for 24 hours
        )
    }

    private fun createPendingUser(): User {
        return User(
            id = testUserId,
            email = "customer@example.com",
            passwordHash = "\$argon2id\$v=19\$m=65536,t=3,p=4\$somesalt\$somehash",
            firstName = "Jane",
            lastName = "Doe",
            status = UserStatus.PENDING_VERIFICATION,
            tosAcceptedAt = Instant.now(),
            registrationSource = RegistrationSource.WEB
        )
    }
}
