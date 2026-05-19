package com.acme.identity.application

import com.acme.identity.domain.ReactivationToken
import com.acme.identity.domain.RegistrationSource
import com.acme.identity.domain.User
import com.acme.identity.domain.UserStatus
import com.acme.identity.domain.events.ReactivationRequested
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.EventStoreRepository
import com.acme.identity.infrastructure.persistence.ReactivationTokenRepository
import com.acme.identity.infrastructure.persistence.UserRepository
import com.acme.identity.infrastructure.security.PasswordHasher
import com.acme.identity.infrastructure.security.ReactivationTokenGenerator
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

class ReactivateAccountUseCaseTest {
    private lateinit var userRepository: UserRepository
    private lateinit var tokenRepository: ReactivationTokenRepository
    private lateinit var eventStore: EventStoreRepository
    private lateinit var publisher: UserEventPublisher
    private lateinit var passwordHasher: PasswordHasher
    private lateinit var tokenGenerator: ReactivationTokenGenerator
    private lateinit var useCase: ReactivateAccountUseCase

    private val expiresAt = Instant.parse("2026-06-01T00:00:00Z")

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        tokenRepository = mockk()
        eventStore = mockk(relaxed = true)
        publisher = mockk(relaxed = true)
        passwordHasher = mockk()
        tokenGenerator = mockk()

        every { passwordHasher.hash(any()) } returns "dummy_hash"
        every { tokenGenerator.generate() } returns "generated_token_xyz"
        every { tokenGenerator.calculateExpiration() } returns expiresAt
        every { tokenRepository.save(any()) } answers { firstArg() }

        useCase = ReactivateAccountUseCase(
            userRepository = userRepository,
            reactivationTokenRepository = tokenRepository,
            eventStoreRepository = eventStore,
            userEventPublisher = publisher,
            passwordHasher = passwordHasher,
            reactivationTokenGenerator = tokenGenerator,
            meterRegistry = SimpleMeterRegistry()
        )
    }

    @Test
    fun `issues a token and publishes event for deactivated user with correct password`() {
        val user = deactivatedUser("customer@example.com", "real_hash")
        every { userRepository.findByEmail("customer@example.com") } returns user
        every { passwordHasher.verify("CorrectP@ss1", "real_hash") } returns true
        every {
            publisher.publishReactivationRequested(any())
        } returns CompletableFuture.completedFuture(null)

        val tokenSlot = slot<ReactivationToken>()
        every { tokenRepository.save(capture(tokenSlot)) } answers { tokenSlot.captured }

        val result = useCase.execute("customer@example.com", "CorrectP@ss1")

        assertEquals(ReactivateResult.Success, result)
        assertEquals("generated_token_xyz", tokenSlot.captured.token)
        assertEquals(user.id, tokenSlot.captured.userId)

        val eventSlot = slot<ReactivationRequested>()
        verify { eventStore.append(capture(eventSlot)) }
        assertEquals(user.id, eventSlot.captured.payload.userId)
        assertEquals("generated_token_xyz", eventSlot.captured.payload.reactivationToken)

        verify { publisher.publishReactivationRequested(any()) }
    }

    @Test
    fun `returns Success without issuing a token for an unknown email`() {
        every { userRepository.findByEmail("ghost@example.com") } returns null
        every { passwordHasher.verify("AnyPassword!", "dummy_hash") } returns false

        val result = useCase.execute("ghost@example.com", "AnyPassword!")

        assertEquals(ReactivateResult.Success, result)
        verify(exactly = 0) { tokenRepository.save(any()) }
        verify(exactly = 0) { eventStore.append(any()) }
        verify(exactly = 0) { publisher.publishReactivationRequested(any()) }
        // Dummy hash verify still runs to keep timing consistent.
        verify { passwordHasher.verify("AnyPassword!", "dummy_hash") }
    }

    @Test
    fun `returns Success without issuing a token when account is not deactivated`() {
        val user = deactivatedUser("active@example.com", "real_hash").apply {
            status = UserStatus.ACTIVE
        }
        every { userRepository.findByEmail("active@example.com") } returns user
        every { passwordHasher.verify("CorrectP@ss1", "real_hash") } returns true

        val result = useCase.execute("active@example.com", "CorrectP@ss1")

        assertEquals(ReactivateResult.Success, result)
        verify(exactly = 0) { tokenRepository.save(any()) }
        verify(exactly = 0) { eventStore.append(any()) }
        verify { passwordHasher.verify("CorrectP@ss1", "real_hash") }
    }

    @Test
    fun `returns Success without issuing a token when password is wrong`() {
        val user = deactivatedUser("customer@example.com", "real_hash")
        every { userRepository.findByEmail("customer@example.com") } returns user
        every { passwordHasher.verify("WrongPassword!", "real_hash") } returns false

        val result = useCase.execute("customer@example.com", "WrongPassword!")

        assertEquals(ReactivateResult.Success, result)
        verify(exactly = 0) { tokenRepository.save(any()) }
        verify(exactly = 0) { eventStore.append(any()) }
        verify(exactly = 0) { publisher.publishReactivationRequested(any()) }
    }

    @Test
    fun `lowercases the email before lookup`() {
        every { userRepository.findByEmail("user@example.com") } returns null
        every { passwordHasher.verify("x", "dummy_hash") } returns false

        useCase.execute("User@Example.COM", "x")

        verify { userRepository.findByEmail("user@example.com") }
    }

    private fun deactivatedUser(email: String, passwordHash: String): User {
        return User(
            id = UUID.randomUUID(),
            email = email,
            passwordHash = passwordHash,
            firstName = "Test",
            lastName = "User",
            status = UserStatus.DEACTIVATED,
            tosAcceptedAt = Instant.now(),
            marketingOptIn = false,
            registrationSource = RegistrationSource.WEB
        ).apply {
            deactivatedAt = Instant.parse("2025-12-01T00:00:00Z")
        }
    }
}
