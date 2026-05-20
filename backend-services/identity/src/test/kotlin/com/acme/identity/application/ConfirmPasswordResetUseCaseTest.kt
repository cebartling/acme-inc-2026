package com.acme.identity.application

import com.acme.identity.domain.PasswordResetToken
import com.acme.identity.domain.RegistrationSource
import com.acme.identity.domain.Session
import com.acme.identity.domain.User
import com.acme.identity.domain.UserStatus
import com.acme.identity.domain.events.DeviceRevocationReason
import com.acme.identity.domain.events.PasswordChanged
import com.acme.identity.domain.events.PasswordChangeReason
import com.acme.identity.domain.events.SessionInvalidated
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.EventStoreRepository
import com.acme.identity.infrastructure.persistence.PasswordResetTokenRepository
import com.acme.identity.infrastructure.persistence.SessionRepository
import com.acme.identity.infrastructure.persistence.UserRepository
import com.acme.identity.infrastructure.security.PasswordHasher
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfirmPasswordResetUseCaseTest {
    private lateinit var tokenRepository: PasswordResetTokenRepository
    private lateinit var userRepository: UserRepository
    private lateinit var sessionRepository: SessionRepository
    private lateinit var deviceTrustService: DeviceTrustService
    private lateinit var passwordHasher: PasswordHasher
    private lateinit var eventStore: EventStoreRepository
    private lateinit var publisher: UserEventPublisher
    private lateinit var useCase: ConfirmPasswordResetUseCase

    @BeforeEach
    fun setUp() {
        tokenRepository = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        sessionRepository = mockk(relaxed = true)
        deviceTrustService = mockk(relaxed = true)
        passwordHasher = mockk()
        eventStore = mockk(relaxed = true)
        publisher = mockk(relaxed = true)

        every { passwordHasher.hash(any()) } returns "new_hash"
        every { tokenRepository.save(any<PasswordResetToken>()) } answers { firstArg() }
        every { userRepository.save(any<User>()) } answers { firstArg() }
        every { sessionRepository.findByUserId(any()) } returns emptyList()
        every { deviceTrustService.revokeAllDevices(any(), any(), any()) } returns 0
        every { publisher.publish(any<SessionInvalidated>()) } returns CompletableFuture.completedFuture(null)
        every { publisher.publishPasswordChanged(any()) } returns CompletableFuture.completedFuture(null)

        useCase = ConfirmPasswordResetUseCase(
            passwordResetTokenRepository = tokenRepository,
            userRepository = userRepository,
            sessionRepository = sessionRepository,
            deviceTrustService = deviceTrustService,
            passwordHasher = passwordHasher,
            eventStoreRepository = eventStore,
            userEventPublisher = publisher,
            meterRegistry = SimpleMeterRegistry()
        )
    }

    @Test
    fun `successful reset updates password, marks token used, publishes PasswordChanged`() {
        val user = activeUser()
        val token = freshToken(user.id)
        every { tokenRepository.findByToken("rst_ok") } returns token
        every { userRepository.findById(user.id) } returns Optional.of(user)

        val userSlot = slot<User>()
        every { userRepository.save(capture(userSlot)) } answers { userSlot.captured }

        val result = useCase.execute(
            token = "rst_ok",
            newPassword = "NewSecureP@ss123",
            ipAddress = "127.0.0.1"
        )

        assertIs<ConfirmPasswordResetResult.Success>(result)
        assertEquals("new_hash", userSlot.captured.passwordHash)
        assertTrue(token.isUsed())

        val passwordChangedSlot = slot<PasswordChanged>()
        verify { eventStore.append(capture(passwordChangedSlot)) }
        // Multiple events may be appended; at least one PasswordChanged with PASSWORD_RESET.
        verify { publisher.publishPasswordChanged(match { it.payload.reason == PasswordChangeReason.PASSWORD_RESET }) }
    }

    @Test
    fun `clears lockout and failed attempts on reset`() {
        val user = activeUser().apply {
            failedAttempts = 4
            lockedUntil = Instant.now().plus(10, ChronoUnit.MINUTES)
            status = UserStatus.LOCKED
        }
        val token = freshToken(user.id)
        every { tokenRepository.findByToken("rst_lock") } returns token
        every { userRepository.findById(user.id) } returns Optional.of(user)

        val userSlot = slot<User>()
        every { userRepository.save(capture(userSlot)) } answers { userSlot.captured }

        useCase.execute(token = "rst_lock", newPassword = "NewSecureP@ss123", ipAddress = null)

        assertEquals(0, userSlot.captured.failedAttempts)
        assertNull(userSlot.captured.lockedUntil)
        assertEquals(UserStatus.ACTIVE, userSlot.captured.status)
    }

    @Test
    fun `revokes all sessions and device trusts on reset`() {
        val user = activeUser()
        val token = freshToken(user.id)
        every { tokenRepository.findByToken("rst_revoke") } returns token
        every { userRepository.findById(user.id) } returns Optional.of(user)

        val session1 = Session.create(userId = user.id, deviceId = "d1", ipAddress = "1.1.1.1", userAgent = "ua", tokenFamily = "tf1")
        val session2 = Session.create(userId = user.id, deviceId = "d2", ipAddress = "1.1.1.2", userAgent = "ua", tokenFamily = "tf2")
        every { sessionRepository.findByUserId(user.id) } returns listOf(session1, session2)
        every { deviceTrustService.revokeAllDevices(user.id, DeviceRevocationReason.PASSWORD_CHANGED, any()) } returns 3

        val result = useCase.execute(token = "rst_revoke", newPassword = "NewSecureP@ss123", ipAddress = null)
            as ConfirmPasswordResetResult.Success

        assertEquals(2, result.sessionsInvalidated)
        assertEquals(3, result.deviceTrustsRevoked)
        verify(exactly = 2) { sessionRepository.delete(any<Session>()) }
        verify { deviceTrustService.revokeAllDevices(user.id, DeviceRevocationReason.PASSWORD_CHANGED, any()) }
    }

    @Test
    fun `rejects unknown token`() {
        every { tokenRepository.findByToken("missing") } returns null
        val result = useCase.execute("missing", "NewSecureP@ss123", null)
        assertEquals(ConfirmPasswordResetResult.InvalidToken, result)
    }

    @Test
    fun `rejects expired token`() {
        val token = PasswordResetToken(
            id = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            token = "rst_expired",
            expiresAt = Instant.now().minus(1, ChronoUnit.MINUTES)
        )
        every { tokenRepository.findByToken("rst_expired") } returns token
        val result = useCase.execute("rst_expired", "NewSecureP@ss123", null)
        assertEquals(ConfirmPasswordResetResult.TokenExpired, result)
    }

    @Test
    fun `rejects already-used token`() {
        val token = PasswordResetToken(
            id = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            token = "rst_used",
            expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES),
            usedAt = Instant.now().minus(1, ChronoUnit.MINUTES)
        )
        every { tokenRepository.findByToken("rst_used") } returns token
        val result = useCase.execute("rst_used", "NewSecureP@ss123", null)
        assertEquals(ConfirmPasswordResetResult.TokenAlreadyUsed, result)
    }

    @Test
    fun `rejects weak password with unmet requirements`() {
        val user = activeUser()
        val token = freshToken(user.id)
        every { tokenRepository.findByToken("rst_weak") } returns token
        every { userRepository.findById(user.id) } returns Optional.of(user)

        val result = useCase.execute("rst_weak", "weak", null)
        val requirementsResult = assertIs<ConfirmPasswordResetResult.PasswordRequirementsNotMet>(result)
        assertTrue(requirementsResult.requirements.any { !it.met })
        // User should NOT have been saved.
        verify(exactly = 0) { userRepository.save(any()) }
    }

    private fun activeUser(): User = User(
        id = UUID.randomUUID(),
        email = "user@example.com",
        passwordHash = "old_hash",
        firstName = "Test",
        lastName = "User",
        status = UserStatus.ACTIVE,
        tosAcceptedAt = Instant.now(),
        marketingOptIn = false,
        registrationSource = RegistrationSource.WEB
    )

    private fun freshToken(userId: UUID) = PasswordResetToken(
        id = UUID.randomUUID(),
        userId = userId,
        token = "rst_test",
        expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES)
    )
}
