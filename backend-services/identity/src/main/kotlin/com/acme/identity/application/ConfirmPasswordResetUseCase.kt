package com.acme.identity.application

import com.acme.identity.domain.UserStatus
import com.acme.identity.domain.events.DeviceRevocationReason
import com.acme.identity.domain.events.PasswordChangeReason
import com.acme.identity.domain.events.PasswordChanged
import com.acme.identity.domain.events.SessionInvalidated
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.EventStoreRepository
import com.acme.identity.infrastructure.persistence.PasswordResetTokenRepository
import com.acme.identity.infrastructure.persistence.SessionRepository
import com.acme.identity.infrastructure.persistence.UserRepository
import com.acme.identity.infrastructure.security.PasswordHasher
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

sealed interface ConfirmPasswordResetResult {
    data class Success(
        val sessionsInvalidated: Int,
        val deviceTrustsRevoked: Int
    ) : ConfirmPasswordResetResult

    data object InvalidToken : ConfirmPasswordResetResult
    data object TokenExpired : ConfirmPasswordResetResult
    data object TokenAlreadyUsed : ConfirmPasswordResetResult
    data class PasswordRequirementsNotMet(
        val requirements: List<PasswordRequirement>
    ) : ConfirmPasswordResetResult
}

/**
 * Completes a password reset:
 * 1. Validate token (exists, not used, not expired).
 * 2. Validate new password against [PasswordRequirements].
 * 3. Update user password hash (Argon2id).
 * 4. Clear account lockout and reset failed-attempt counter.
 * 5. Invalidate all active sessions (with [SessionInvalidated] events).
 * 6. Revoke all device trusts (with DeviceRevoked events).
 * 7. Mark reset token as used.
 * 8. Publish [PasswordChanged].
 */
@Service
class ConfirmPasswordResetUseCase(
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val deviceTrustService: DeviceTrustService,
    private val passwordHasher: PasswordHasher,
    private val eventStoreRepository: EventStoreRepository,
    private val userEventPublisher: UserEventPublisher,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun execute(
        token: String,
        newPassword: String,
        ipAddress: String?,
        correlationId: UUID = UUID.randomUUID()
    ): ConfirmPasswordResetResult {
        val resetToken = passwordResetTokenRepository.findByToken(token)
            ?: return ConfirmPasswordResetResult.InvalidToken.also { incrementCounter("invalid_token") }

        if (resetToken.isUsed()) {
            incrementCounter("token_used")
            return ConfirmPasswordResetResult.TokenAlreadyUsed
        }
        if (resetToken.isExpired()) {
            incrementCounter("token_expired")
            return ConfirmPasswordResetResult.TokenExpired
        }

        val requirements = PasswordRequirements.check(newPassword)
        if (requirements.any { !it.met }) {
            incrementCounter("weak_password")
            return ConfirmPasswordResetResult.PasswordRequirementsNotMet(requirements)
        }

        val user = userRepository.findById(resetToken.userId).orElse(null)
            ?: return ConfirmPasswordResetResult.InvalidToken
                .also { incrementCounter("invalid_token") }

        val updated = user.updatePassword(passwordHasher.hash(newPassword))
        // AC-08: clear lockout + reset failed attempts on reset.
        updated.failedAttempts = 0
        updated.lockedUntil = null
        if (updated.status == UserStatus.LOCKED) {
            updated.status = UserStatus.ACTIVE
        }
        userRepository.save(updated)

        resetToken.markAsUsed()
        passwordResetTokenRepository.save(resetToken)

        val sessions = sessionRepository.findByUserId(updated.id)
        sessions.forEach { session ->
            sessionRepository.delete(session)
            val event = SessionInvalidated.create(
                sessionId = session.id,
                userId = updated.id,
                reason = SessionInvalidated.REASON_PASSWORD_RESET
            )
            eventStoreRepository.append(event)
            userEventPublisher.publish(event)
        }

        val devicesRevoked = deviceTrustService.revokeAllDevices(
            userId = updated.id,
            reason = DeviceRevocationReason.PASSWORD_CHANGED,
            correlationId = correlationId
        )

        val passwordChanged = PasswordChanged.create(
            userId = updated.id,
            reason = PasswordChangeReason.PASSWORD_RESET,
            sessionsInvalidated = sessions.size,
            deviceTrustsRevoked = devicesRevoked,
            ipAddress = ipAddress,
            correlationId = correlationId
        )
        eventStoreRepository.append(passwordChanged)
        userEventPublisher.publishPasswordChanged(passwordChanged)

        logger.info(
            "Password reset completed for user {} (sessions={}, devices={}, at={})",
            updated.id, sessions.size, devicesRevoked, Instant.now()
        )
        incrementCounter("success")

        return ConfirmPasswordResetResult.Success(
            sessionsInvalidated = sessions.size,
            deviceTrustsRevoked = devicesRevoked
        )
    }

    private fun incrementCounter(result: String) {
        meterRegistry.counter("password_reset_confirm_total", "result", result).increment()
    }
}
