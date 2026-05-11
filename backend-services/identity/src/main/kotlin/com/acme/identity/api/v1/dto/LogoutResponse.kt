package com.acme.identity.api.v1.dto

/**
 * Response DTO for a single-session logout.
 *
 * @property status Outcome marker ("SUCCESS").
 * @property message Human-readable confirmation.
 */
data class LogoutResponse(
    val status: String = "SUCCESS",
    val message: String = "You have been signed out."
)

/**
 * Response DTO for logout-all-devices.
 *
 * @property status Outcome marker ("SUCCESS").
 * @property message Human-readable confirmation.
 * @property sessionsInvalidated Number of sessions that were invalidated.
 */
data class LogoutAllResponse(
    val status: String = "SUCCESS",
    val message: String = "You have been signed out from all devices.",
    val sessionsInvalidated: Int
)
