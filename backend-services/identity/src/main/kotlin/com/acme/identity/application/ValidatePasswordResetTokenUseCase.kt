package com.acme.identity.application

import com.acme.identity.infrastructure.persistence.PasswordResetTokenRepository
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

sealed interface PasswordResetTokenValidation {
    data class Valid(val expiresInSeconds: Long) : PasswordResetTokenValidation
    data object Invalid : PasswordResetTokenValidation
    data object Expired : PasswordResetTokenValidation
    data object AlreadyUsed : PasswordResetTokenValidation
}

/**
 * Validates a password-reset token without consuming it. Used by the
 * frontend to decide whether to render the new-password form or an
 * expired-link message.
 */
@Service
class ValidatePasswordResetTokenUseCase(
    private val passwordResetTokenRepository: PasswordResetTokenRepository
) {
    fun execute(token: String): PasswordResetTokenValidation {
        val resetToken = passwordResetTokenRepository.findByToken(token)
            ?: return PasswordResetTokenValidation.Invalid

        if (resetToken.isUsed()) return PasswordResetTokenValidation.AlreadyUsed
        if (resetToken.isExpired()) return PasswordResetTokenValidation.Expired

        return PasswordResetTokenValidation.Valid(
            expiresInSeconds = Duration.between(Instant.now(), resetToken.expiresAt).seconds
        )
    }
}
