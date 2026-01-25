package com.acme.identity.application

import com.acme.identity.domain.events.DeviceRevocationReason
import com.acme.identity.infrastructure.persistence.UserRepository
import com.acme.identity.infrastructure.security.PasswordHasher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Use case for changing a user's password.
 *
 * After a successful password change:
 * 1. Validates current password
 * 2. Hashes new password with Argon2id
 * 3. Updates user.passwordHash
 * 4. **Revokes all device trusts** to prevent MFA bypass with old credentials
 *
 * ## Security Rationale
 *
 * Revoking device trusts on password change is critical because:
 * - If the old password was compromised, the attacker may have trusted their device
 * - Prevents persistent access after credential change
 * - Ensures the user regains full control of their account
 * - Required by security compliance frameworks
 *
 * @see DeviceTrustService.revokeAllDevices
 * @see DeviceRevocationReason.PASSWORD_CHANGED
 */
@Service
class ChangePasswordUseCase(
    private val userRepository: UserRepository,
    private val passwordHasher: PasswordHasher,
    private val deviceTrustService: DeviceTrustService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Changes a user's password.
     *
     * Steps:
     * 1. Validate current password
     * 2. Hash new password with Argon2id
     * 3. Update user.passwordHash
     * 4. **CRITICAL**: Revoke all device trusts
     * 5. Return success
     *
     * @param userId The user's unique ID
     * @param currentPassword The user's current password (for verification)
     * @param newPassword The new password to set
     * @param correlationId Correlation ID for distributed tracing
     * @return Result indicating success or failure
     */
    fun execute(
        userId: UUID,
        currentPassword: String,
        newPassword: String,
        correlationId: UUID = UUID.randomUUID()
    ): ChangePasswordResult {
        logger.info("Changing password for user $userId")

        // Find the user
        val user = userRepository.findById(userId).orElse(null)
            ?: return ChangePasswordResult.InternalError("User not found")

        // Verify current password
        if (!passwordHasher.verify(currentPassword, user.passwordHash)) {
            logger.warn("Invalid current password for user $userId")
            return ChangePasswordResult.InvalidCurrentPassword("Current password is incorrect")
        }

        // Validate new password (basic validation - should match registration requirements)
        if (newPassword.length < 8) {
            return ChangePasswordResult.WeakPassword("Password must be at least 8 characters")
        }

        // Hash the new password
        val newPasswordHash = passwordHasher.hash(newPassword)

        // Update the user's password
        val updatedUser = user.updatePassword(newPasswordHash)
        userRepository.save(updatedUser)

        // CRITICAL: Revoke all device trusts for security
        val revokedCount = deviceTrustService.revokeAllDevices(
            userId = userId,
            reason = DeviceRevocationReason.PASSWORD_CHANGED,
            correlationId = correlationId
        )

        logger.info("Password changed successfully for user $userId, revoked $revokedCount device trusts")
        return ChangePasswordResult.Success
    }
}

/**
 * Result of a password change operation.
 */
sealed interface ChangePasswordResult {
    data object Success : ChangePasswordResult
    data class InvalidCurrentPassword(val message: String) : ChangePasswordResult
    data class WeakPassword(val message: String) : ChangePasswordResult
    data class InternalError(val message: String) : ChangePasswordResult
}
