package com.acme.identity.infrastructure.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

/**
 * Generator for cryptographically secure email verification tokens.
 *
 * Tokens are generated using [SecureRandom] and encoded as URL-safe
 * Base64 strings, making them suitable for inclusion in verification links.
 *
 * Each token is 32 bytes (256 bits) of random data, providing sufficient
 * entropy to prevent brute-force attacks.
 *
 * @property expirationHours How long tokens remain valid. Defaults to 24 hours.
 */
@Component
class VerificationTokenGenerator(
    @Value("\${identity.verification-token.expiration-hours:24}")
    private val expirationHours: Long
) {
    private val secureRandom = SecureRandom()
    private val base64Encoder = Base64.getUrlEncoder().withoutPadding()

    /**
     * Generates a new cryptographically secure verification token.
     *
     * The token is 32 bytes of random data encoded as URL-safe Base64,
     * resulting in a 43-character string.
     *
     * @return A URL-safe Base64 encoded token string.
     */
    fun generate(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return base64Encoder.encodeToString(bytes)
    }

    /**
     * Calculates the expiration timestamp for a new token.
     *
     * @return The [Instant] when a token created now would expire.
     */
    fun calculateExpiration(): Instant {
        return Instant.now().plus(expirationHours, ChronoUnit.HOURS)
    }
}
