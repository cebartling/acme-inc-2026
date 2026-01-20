package com.acme.identity.api.v1.dto

import com.fasterxml.jackson.annotation.JsonInclude
import java.util.UUID

/**
 * Represents the status of a signin response.
 */
enum class SigninStatus {
    /** Authentication successful, user is fully signed in. */
    SUCCESS,

    /** Credentials valid, but MFA verification is required. */
    MFA_REQUIRED
}

/**
 * Available MFA methods for a user.
 */
enum class MfaMethod {
    /** Time-based One-Time Password (TOTP) via authenticator app. */
    TOTP,

    /** One-time code sent via SMS. */
    SMS,

    /** One-time code sent via email. */
    EMAIL
}

/**
 * Response DTO for successful signin.
 *
 * Contains information about the authenticated session or MFA requirements.
 *
 * @property status The status of the signin attempt.
 * @property userId The authenticated user's ID (present when status is SUCCESS).
 * @property mfaToken A temporary token for MFA verification (present when status is MFA_REQUIRED).
 * @property mfaMethods Available MFA methods for the user (present when status is MFA_REQUIRED).
 * @property expiresIn Token/session expiration time in seconds.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SigninResponse(
    val status: SigninStatus,
    val userId: UUID? = null,
    val mfaToken: String? = null,
    val mfaMethods: List<MfaMethod>? = null,
    val expiresIn: Int
) {
    companion object {
        /** Default access token expiration: 15 minutes. */
        const val DEFAULT_ACCESS_TOKEN_EXPIRY_SECONDS = 900

        /** Default MFA token expiration: 5 minutes. */
        const val DEFAULT_MFA_TOKEN_EXPIRY_SECONDS = 300

        /**
         * Creates a success response for a user without MFA.
         */
        fun success(userId: UUID, expiresIn: Int = DEFAULT_ACCESS_TOKEN_EXPIRY_SECONDS): SigninResponse {
            return SigninResponse(
                status = SigninStatus.SUCCESS,
                userId = userId,
                expiresIn = expiresIn
            )
        }

        /**
         * Creates an MFA required response.
         */
        fun mfaRequired(
            mfaToken: String,
            mfaMethods: List<MfaMethod>,
            expiresIn: Int = DEFAULT_MFA_TOKEN_EXPIRY_SECONDS
        ): SigninResponse {
            return SigninResponse(
                status = SigninStatus.MFA_REQUIRED,
                mfaToken = mfaToken,
                mfaMethods = mfaMethods,
                expiresIn = expiresIn
            )
        }
    }
}

/**
 * Response DTO for signin errors.
 *
 * Used for 401, 403, 423, and 429 responses.
 *
 * @property error The error code.
 * @property message Human-readable error message.
 * @property remainingAttempts Number of signin attempts remaining before lockout (optional).
 * @property reason Additional reason code for account status errors (optional).
 * @property supportUrl URL for customer support (optional).
 * @property lockedUntil Timestamp when the lockout expires (optional, for 423 responses).
 * @property lockoutRemainingSeconds Seconds remaining until lockout expires (optional, for 423 responses).
 * @property passwordResetUrl URL for password reset to bypass lockout (optional, for 423 responses).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SigninErrorResponse(
    val error: String,
    val message: String,
    val remainingAttempts: Int? = null,
    val reason: String? = null,
    val supportUrl: String? = null,
    val lockedUntil: String? = null,
    val lockoutRemainingSeconds: Long? = null,
    val passwordResetUrl: String? = null
)
