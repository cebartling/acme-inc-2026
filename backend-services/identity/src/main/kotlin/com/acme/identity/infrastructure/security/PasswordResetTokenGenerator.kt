package com.acme.identity.infrastructure.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

/**
 * Generates cryptographically secure single-use tokens for the password
 * reset flow. Mirrors [ReactivationTokenGenerator]'s design (32 bytes of
 * entropy, URL-safe Base64) but with a configurable 1-hour TTL per
 * US-0003-13.
 */
@Component
class PasswordResetTokenGenerator(
    @Value("\${identity.password-reset-token.expiration-minutes:60}")
    private val expirationMinutes: Long
) {
    private val secureRandom = SecureRandom()
    private val base64Encoder = Base64.getUrlEncoder().withoutPadding()

    fun generate(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return "rst_" + base64Encoder.encodeToString(bytes)
    }

    fun calculateExpiration(): Instant {
        return Instant.now().plus(expirationMinutes, ChronoUnit.MINUTES)
    }

    fun expirationMinutes(): Long = expirationMinutes
}
