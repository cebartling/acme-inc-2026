package com.acme.identity.api.v1.dto

import com.acme.identity.application.PasswordRequirement
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** Request body for POST /api/v1/auth/password-reset. */
data class PasswordResetRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    @field:Size(max = 255, message = "Email must not exceed 255 characters")
    val email: String
)

/**
 * Response body for POST /api/v1/auth/password-reset. Always 200, with
 * a generic message regardless of whether the email maps to an account.
 */
data class PasswordResetResponse(
    val message: String =
        "If an account exists with this email, a password reset link has been sent."
)

/** Response body for GET /api/v1/auth/password-reset/{token} on success. */
data class PasswordResetTokenValidResponse(
    val valid: Boolean = true,
    val expiresIn: Long
)

/** Request body for POST /api/v1/auth/password-reset/confirm. */
data class PasswordResetConfirmRequest(
    @field:NotBlank(message = "Token is required")
    @field:Size(max = 255)
    val token: String,

    @field:NotBlank(message = "New password is required")
    @field:Size(max = 128, message = "Password must not exceed 128 characters")
    val newPassword: String
)

/** Response body for POST /api/v1/auth/password-reset/confirm on success. */
data class PasswordResetConfirmResponse(
    val message: String =
        "Your password has been updated. Please sign in with your new password.",
    val sessionsInvalidated: Int,
    val deviceTrustsRevoked: Int
)

/** Response body when the new password fails requirements. */
data class PasswordRequirementsErrorResponse(
    val error: String = "PASSWORD_REQUIREMENTS_NOT_MET",
    val message: String = "Password does not meet requirements",
    val requirements: List<PasswordRequirement>
)

/** Response body when the reset token is invalid, expired, or used. */
data class PasswordResetTokenErrorResponse(
    val error: String = "INVALID_RESET_TOKEN",
    val message: String = "This password reset link is invalid or has expired.",
    val requestNewUrl: String = "/forgot-password"
)
