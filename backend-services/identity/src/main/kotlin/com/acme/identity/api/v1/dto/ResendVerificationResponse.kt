package com.acme.identity.api.v1.dto

import java.time.Instant

/**
 * Response body for a successful resend verification request.
 *
 * @property message Human-readable message about the request outcome.
 * @property timestamp When the response was generated.
 * @property requestsRemaining Number of resend requests remaining in the rate limit window.
 */
data class ResendVerificationResponse(
    val message: String,
    val timestamp: Instant = Instant.now(),
    val requestsRemaining: Int? = null
)
