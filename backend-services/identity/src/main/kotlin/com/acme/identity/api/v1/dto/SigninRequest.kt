package com.acme.identity.api.v1.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Request DTO for user signin (authentication).
 *
 * Contains the credentials and optional metadata required to authenticate a user.
 * All fields are validated using Jakarta Bean Validation annotations.
 *
 * @property email The user's email address used for authentication.
 * @property password The user's password.
 * @property rememberMe Whether to create a long-lived session. Defaults to `false`.
 * @property deviceFingerprint Optional device fingerprint for fraud detection.
 */
data class SigninRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    @field:Size(max = 255, message = "Email must not exceed 255 characters")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(max = 128, message = "Password must not exceed 128 characters")
    val password: String,

    val rememberMe: Boolean = false,

    @field:Size(max = 255, message = "Device fingerprint must not exceed 255 characters")
    val deviceFingerprint: String? = null
)
