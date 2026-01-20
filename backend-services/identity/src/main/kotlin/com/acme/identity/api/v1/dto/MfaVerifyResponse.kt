package com.acme.identity.api.v1.dto

import com.fasterxml.jackson.annotation.JsonInclude
import java.util.UUID

/**
 * Response DTO for successful MFA verification.
 *
 * @property status The verification status (SUCCESS).
 * @property userId The authenticated user's ID.
 * @property deviceTrusted Whether the device was marked as trusted.
 * @property expiresIn Token expiration time in seconds.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MfaVerifyResponse(
    val status: String = "SUCCESS",
    val userId: UUID,
    val deviceTrusted: Boolean,
    val expiresIn: Int = DEFAULT_ACCESS_TOKEN_EXPIRY_SECONDS
) {
    companion object {
        /** Default access token expiration: 15 minutes. */
        const val DEFAULT_ACCESS_TOKEN_EXPIRY_SECONDS = 900
    }
}

/**
 * Response DTO for MFA verification errors.
 *
 * @property error The error code.
 * @property message Human-readable error message.
 * @property remainingAttempts Number of verification attempts remaining (optional).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MfaVerifyErrorResponse(
    val error: String,
    val message: String,
    val remainingAttempts: Int? = null
)
