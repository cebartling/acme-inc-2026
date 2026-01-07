package com.acme.identity.api.v1.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

/**
 * Request body for resending a verification email.
 *
 * @property email The email address to resend the verification to.
 */
data class ResendVerificationRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String
)
