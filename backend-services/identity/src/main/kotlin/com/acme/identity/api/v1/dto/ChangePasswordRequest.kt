package com.acme.identity.api.v1.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Request DTO for password change.
 *
 * @property currentPassword The user's current password for verification.
 * @property newPassword The new password to set.
 */
data class ChangePasswordRequest(
    @field:NotBlank(message = "Current password is required")
    val currentPassword: String,

    @field:NotBlank(message = "New password is required")
    @field:Size(min = 8, message = "Password must be at least 8 characters")
    val newPassword: String
)

/**
 * Response DTO for successful password change.
 *
 * @property message Success message.
 */
data class ChangePasswordResponse(
    val message: String = "Password changed successfully"
)
