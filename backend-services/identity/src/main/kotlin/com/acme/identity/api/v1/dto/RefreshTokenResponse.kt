package com.acme.identity.api.v1.dto

/**
 * Response body for a successful `POST /api/v1/auth/refresh`.
 *
 * The new access and refresh tokens are returned in `Set-Cookie` headers
 * (HttpOnly), not in the body — this DTO only carries the success marker
 * and the access-token TTL in seconds so the client can schedule its next
 * refresh proactively if it wishes.
 */
data class RefreshTokenResponse(
    val status: String = "SUCCESS",
    val expiresIn: Long
)
