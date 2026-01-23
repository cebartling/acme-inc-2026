package com.acme.identity.application

/**
 * Represents a pair of JWT tokens (access and refresh).
 *
 * Returned after successful authentication or token refresh.
 *
 * @property accessToken The JWT access token (15-minute expiry).
 * @property refreshToken The JWT refresh token (7-day expiry).
 * @property accessTokenExpiry Access token expiry in seconds.
 * @property refreshTokenExpiry Refresh token expiry in seconds.
 */
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiry: Long,
    val refreshTokenExpiry: Long
)
