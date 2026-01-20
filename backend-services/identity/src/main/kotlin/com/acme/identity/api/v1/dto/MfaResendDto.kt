package com.acme.identity.api.v1.dto

import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.validation.constraints.NotBlank

/**
 * Request DTO for resending an SMS verification code.
 *
 * @property mfaToken The MFA challenge token.
 * @property method The MFA method (must be SMS).
 */
data class MfaResendRequest(
    @field:NotBlank(message = "MFA token is required")
    val mfaToken: String,

    val method: String = "SMS"
)

/**
 * Response DTO for successful SMS code resend.
 *
 * @property status The status (CODE_SENT).
 * @property maskedPhone The masked phone number (e.g., ***-***-1234).
 * @property expiresIn Seconds until the code expires.
 * @property resendAvailableIn Seconds until another resend is allowed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MfaResendResponse(
    val status: String = "CODE_SENT",
    val maskedPhone: String,
    val expiresIn: Long,
    val resendAvailableIn: Long
)

/**
 * Error response for SMS resend failures.
 *
 * @property error The error code.
 * @property message Human-readable error message.
 * @property resendAvailableIn Seconds until resend is available (for cooldown errors).
 * @property retryAfter Seconds until rate limit resets (for rate limit errors).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MfaResendErrorResponse(
    val error: String,
    val message: String,
    val resendAvailableIn: Long? = null,
    val retryAfter: Long? = null
)
