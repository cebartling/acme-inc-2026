package com.acme.identity.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * Configuration properties for JWT token generation.
 *
 * Maps to the `identity.jwt` configuration namespace in application.yml.
 *
 * @property issuer The JWT issuer (iss claim) - typically the auth server URL.
 * @property audience The JWT audience (aud claim) - typically the API server URL.
 * @property accessTokenExpiryMinutes Access token expiry duration in minutes.
 * @property refreshTokenExpiryDays Refresh token expiry duration in days.
 * @property keyRotationPeriodDays Key rotation period in days.
 */
@Configuration
@ConfigurationProperties(prefix = "identity.jwt")
data class JwtConfig(
    var issuer: String = "https://auth.acme.com",
    var audience: String = "https://api.acme.com",
    var accessTokenExpiryMinutes: Long = 15,
    var refreshTokenExpiryDays: Long = 7,
    var keyRotationPeriodDays: Long = 30
) {
    /**
     * Gets the access token expiry duration.
     */
    val accessTokenExpiry: Duration
        get() = Duration.ofMinutes(accessTokenExpiryMinutes)

    /**
     * Gets the refresh token expiry duration.
     */
    val refreshTokenExpiry: Duration
        get() = Duration.ofDays(refreshTokenExpiryDays)

    /**
     * Gets the key rotation period duration.
     */
    val keyRotationPeriod: Duration
        get() = Duration.ofDays(keyRotationPeriodDays)
}
