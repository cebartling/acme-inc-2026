package com.acme.identity.api

import com.acme.identity.application.TokenService
import com.acme.identity.config.JwtConfig
import com.acme.identity.domain.RegistrationSource
import com.acme.identity.domain.User
import com.acme.identity.domain.UserStatus
import com.acme.identity.infrastructure.security.SigningKeyProvider
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JwksControllerTest {

    private val jwtConfig = JwtConfig(
        issuer = "https://auth.acme.com",
        audience = "https://api.acme.com",
        accessTokenExpiryMinutes = 15,
        refreshTokenExpiryDays = 7,
        keyRotationPeriodDays = 30
    )
    private val keyProvider = SigningKeyProvider(jwtConfig)
    private val controller = JwksController(keyProvider)

    private fun publishedSet(): JWKSet = JWKSet.parse(controller.jwks())

    @Test
    fun `publishes only public key material`() {
        val keys = publishedSet().keys

        assertTrue(keys.isNotEmpty())
        keys.forEach { key ->
            assertFalse(key.isPrivate, "JWKS must not expose private key ${key.keyID}")
            assertEquals("sig", key.keyUse.value)
            assertEquals("RS256", key.algorithm.name)
        }
    }

    @Test
    fun `an access token from TokenService verifies against the published set`() {
        val user = User(
            id = UUID.randomUUID(),
            email = "jwks@example.com",
            passwordHash = "hashed_password",
            firstName = "Jwks",
            lastName = "Test",
            status = UserStatus.ACTIVE,
            tosAcceptedAt = Instant.now(),
            registrationSource = RegistrationSource.WEB,
            emailVerified = true,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val token = TokenService(keyProvider, jwtConfig)
            .createTokens(user, "sess_${UUID.randomUUID()}", "fam_${UUID.randomUUID()}")
            .accessToken

        val jwt = SignedJWT.parse(token)
        val key = publishedSet().getKeyByKeyId(jwt.header.keyID) as RSAKey?
        assertNotNull(key, "the token's kid ${jwt.header.keyID} must be published")

        assertTrue(jwt.verify(RSASSAVerifier(key)))
        assertEquals(user.id.toString(), jwt.jwtClaimsSet.subject)
    }

    @Test
    fun `keeps publishing a rotated-out key so tokens it signed still verify`() {
        val previousKid = keyProvider.getCurrentKey().keyId

        keyProvider.rotateKey()

        val kids = publishedSet().keys.map { it.keyID }
        assertTrue(previousKid in kids, "rotated-out key $previousKid should still be published")
        assertEquals(keyProvider.getCurrentKey().keyId, kids.first())
    }
}
