package com.acme.identity.infrastructure.security

import com.acme.identity.config.JwtConfig
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Behaviour of [SigningKeyProvider] when `identity.jwt.signing-key` is set:
 * every instance given the same key signs and verifies with it, and the key is never rotated.
 */
class SigningKeyProviderConfiguredKeyTest {

    private val pem = newPkcs8Pem()

    private fun configWith(signingKey: String, keyRotationPeriodDays: Long = 30) =
        JwtConfig(keyRotationPeriodDays = keyRotationPeriodDays, signingKey = signingKey)

    private fun providerWith(signingKey: String = pem, keyRotationPeriodDays: Long = 30) =
        SigningKeyProvider(configWith(signingKey, keyRotationPeriodDays))

    @Test
    fun `two instances given the same key share a kid and a public key`() {
        val first = providerWith().getCurrentKey()
        val second = providerWith().getCurrentKey()

        assertEquals(first.keyId, second.keyId)
        assertEquals(first.publicKey, second.publicKey)
        assertEquals("RS256", first.algorithm)
    }

    @Test
    fun `a token signed by one instance verifies on another`() {
        val signer = providerWith()
        val verifier = providerWith()

        val token = sign(signer.getCurrentKey())

        val verifyingKey = verifier.getKey(token.header.keyID)
        assertNotNull(verifyingKey)
        assertTrue(token.verify(RSASSAVerifier(verifyingKey.publicKey as RSAPublicKey)))
    }

    @Test
    fun `a restart keeps an existing token valid`() {
        val token = sign(providerWith().getCurrentKey())

        val restarted = providerWith()

        val verifyingKey = restarted.getKey(token.header.keyID)
        assertNotNull(verifyingKey)
        assertTrue(token.verify(RSASSAVerifier(verifyingKey.publicKey as RSAPublicKey)))
    }

    @Test
    fun `different keys get different kids`() {
        assertTrue(providerWith().getCurrentKey().keyId != providerWith(newPkcs8Pem()).getCurrentKey().keyId)
    }

    @Test
    fun `the only verification key is the configured one`() {
        val provider = providerWith()

        assertEquals(listOf(provider.getCurrentKey()), provider.getVerificationKeys())
    }

    @Test
    fun `autoRotateKey does not replace the key even when the rotation period has elapsed`() {
        // A period of 0 days means "rotate on every run" for a generated key.
        val provider = providerWith(keyRotationPeriodDays = 0)
        val before = provider.getCurrentKey()

        provider.autoRotateKey()

        assertEquals(before, provider.getCurrentKey())
    }

    @Test
    fun `a blank key still auto-rotates once the period has elapsed`() {
        // Guards the no-key path: the configured-key early return must not switch off nightly rotation.
        val provider = providerWith("", keyRotationPeriodDays = 0)
        val before = provider.getCurrentKey()

        provider.autoRotateKey()

        assertNotEquals(before.keyId, provider.getCurrentKey().keyId)
        assertNotNull(provider.getKey(before.keyId))
    }

    @Test
    fun `rotateKey is refused`() {
        val provider = providerWith()

        val error = assertFailsWith<IllegalStateException> { provider.rotateKey() }

        assertTrue(error.message!!.contains("JWT_SIGNING_KEY"), error.message)
    }

    @Test
    fun `a malformed key fails fast and names the setting`() {
        val error = assertFailsWith<IllegalStateException> { providerWith("not a pem") }

        assertTrue(error.message!!.contains("JWT_SIGNING_KEY"), error.message)
    }

    @Test
    fun `a key under 2048 bits fails fast instead of failing every signing later`() {
        val error = assertFailsWith<IllegalStateException> { providerWith(newPkcs8Pem(bits = 1024)) }

        assertTrue(error.message!!.contains("JWT_SIGNING_KEY"), error.message)
        assertTrue(error.cause!!.message!!.contains("at least 2048 bits"), error.cause!!.message)
    }

    @Test
    fun `a blank key keeps generating an in-memory key`() {
        val first = providerWith("").getCurrentKey()
        val second = providerWith("").getCurrentKey()

        assertTrue(first.keyId != second.keyId)
    }

    private fun sign(key: SigningKey): SignedJWT =
        SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyId).build(),
            JWTClaimsSet.Builder().subject("user-1").build()
        ).also { it.sign(RSASSASigner(key.privateKey)) }

    private fun newPkcs8Pem(bits: Int = 2048): String {
        val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(bits) }
        val body = Base64.getMimeEncoder(64, "\n".toByteArray())
            .encodeToString(generator.generateKeyPair().private.encoded)
        return "-----BEGIN PRIVATE KEY-----\n$body\n-----END PRIVATE KEY-----\n"
    }
}
