package com.acme.identity.api.v1.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Request body for POST /api/v1/auth/reactivate.
 *
 * @property email The deactivated account's email.
 * @property password The customer's current password, required to confirm identity.
 */
data class ReactivateAccountRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    @field:Size(max = 255, message = "Email must not exceed 255 characters")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(max = 128, message = "Password must not exceed 128 characters")
    val password: String
)

/**
 * Response body for POST /api/v1/auth/reactivate.
 *
 * The message is intentionally generic to avoid leaking whether the email
 * maps to a real or deactivated account.
 */
data class ReactivateAccountResponse(
    val message: String = "If an eligible account exists for this email, a reactivation email has been sent."
)
