package com.acme.identity.infrastructure.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

/**
 * Generates cryptographically secure single-use tokens for the account
 * reactivation flow. Mirrors [VerificationTokenGenerator]'s design (32 bytes
 * of entropy, URL-safe Base64) but with its own TTL so reactivation
 * lifetimes can be tuned independently of email-verification lifetimes.
 */
@Component
class ReactivationTokenGenerator(
    @Value("\${identity.reactivation-token.expiration-hours:24}")
    private val expirationHours: Long
) {
    private val secureRandom = SecureRandom()
    private val base64Encoder = Base64.getUrlEncoder().withoutPadding()

    fun generate(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return base64Encoder.encodeToString(bytes)
    }

    fun calculateExpiration(): Instant {
        return Instant.now().plus(expirationHours, ChronoUnit.HOURS)
    }
}
