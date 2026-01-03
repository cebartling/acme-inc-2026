package com.acme.identity.infrastructure.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

@Component
class VerificationTokenGenerator(
    @Value("\${identity.verification-token.expiration-hours:24}")
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
