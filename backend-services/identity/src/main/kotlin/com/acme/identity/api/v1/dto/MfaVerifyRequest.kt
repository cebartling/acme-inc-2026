package com.acme.identity.api.v1.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Request DTO for MFA verification.
 *
 * @property mfaToken The MFA challenge token received from signin.
 * @property code The 6-digit verification code from authenticator app.
 * @property method The MFA method being used (TOTP, SMS, EMAIL).
 * @property rememberDevice Whether to remember this device for future logins.
 */
data class MfaVerifyRequest(
    @field:NotBlank(message = "MFA token is required")
    val mfaToken: String,

    @field:NotBlank(message = "Verification code is required")
    @field:Size(min = 6, max = 6, message = "Verification code must be 6 digits")
    @field:Pattern(regexp = "^[0-9]{6}$", message = "Verification code must contain only digits")
    val code: String,

    @field:NotBlank(message = "MFA method is required")
    val method: String = "TOTP",

    val rememberDevice: Boolean = false
)
