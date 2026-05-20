package com.acme.identity.infrastructure.security

import com.acme.identity.config.JwtConfig
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

class SigningKeyProviderTest {

    private lateinit var signingKeyProvider: SigningKeyProvider
    private lateinit var jwtConfig: JwtConfig

    @BeforeEach
    fun setUp() {
        jwtConfig = mockk<JwtConfig>()
        every { jwtConfig.keyRotationPeriodDays } returns 30
        signingKeyProvider = SigningKeyProvider(jwtConfig)
    }

    @Test
    fun `getCurrentKey should return a valid signing key`() {
        val key = signingKeyProvider.getCurrentKey()

        assertNotNull(key)
        assertNotNull(key.keyId)
        assertNotNull(key.privateKey)
        assertNotNull(key.publicKey)
        assertEquals("RS256", key.algorithm)
    }

    @Test
    fun `key ID should follow format key-YYYY-MM-XXXXXXXX`() {
        val key = signingKeyProvider.getCurrentKey()

        // YYYY-MM prefix preserved for human-readable rotation tracking;
        // 8-hex suffix added in SigningKeyProvider to ensure same-month
        // rotations produce distinct kids (so previous-key cache works).
        assertTrue(
            key.keyId.matches(Regex("key-\\d{4}-\\d{2}-[0-9a-f]{8}")),
            "expected key-YYYY-MM-XXXXXXXX, got $key.keyId"
        )
    }

    @Test
    fun `rotateKey retains the previous key under its kid for verification`() {
        // Token verification across rotations relies on the previous-key
        // cache: a token signed under key A still validates after a
        // rotation to key B because A is still resolvable via getKey(A.kid).
        val initial = signingKeyProvider.getCurrentKey()

        signingKeyProvider.rotateKey()

        val rotated = signingKeyProvider.getCurrentKey()
        assertNotEquals(initial.keyId, rotated.keyId)

        // The current kid resolves to the new key.
        assertEquals(rotated.keyId, signingKeyProvider.getKey(rotated.keyId)?.keyId)
        // The previous kid still resolves to the (now-retained) old key.
        val retrieved = signingKeyProvider.getKey(initial.keyId)
        assertNotNull(retrieved)
        assertEquals(initial.privateKey, retrieved.privateKey)
        assertEquals(initial.publicKey, retrieved.publicKey)
    }

    @Test
    fun `rotateKey evicts the oldest previous key once capacity is reached`() {
        val keys = mutableListOf<SigningKey>()
        // Capture the initial + PREVIOUS_KEY_CAPACITY rotations, so the
        // initial key should be evicted by the time we finish.
        keys.add(signingKeyProvider.getCurrentKey())
        repeat(SigningKeyProvider.PREVIOUS_KEY_CAPACITY + 1) {
            signingKeyProvider.rotateKey()
            keys.add(signingKeyProvider.getCurrentKey())
        }
        // keys[0] is the initial key (oldest); it should have been evicted.
        assertNull(signingKeyProvider.getKey(keys[0].keyId))
        // The most recent PREVIOUS_KEY_CAPACITY previous keys are retained,
        // plus the current key, so every keys[1..N] resolves.
        keys.drop(1).forEach { k ->
            assertNotNull(signingKeyProvider.getKey(k.keyId), "kid ${k.keyId} should still resolve")
        }
    }

    @Test
    fun `private and public keys should be RSA`() {
        val key = signingKeyProvider.getCurrentKey()

        assertEquals("RSA", key.privateKey.algorithm)
        assertEquals("RSA", key.publicKey.algorithm)
    }

    @Test
    fun `getCurrentKey should return the same key on multiple calls`() {
        val key1 = signingKeyProvider.getCurrentKey()
        val key2 = signingKeyProvider.getCurrentKey()

        assertEquals(key1.keyId, key2.keyId)
        assertEquals(key1.privateKey, key2.privateKey)
        assertEquals(key1.publicKey, key2.publicKey)
    }

    @Test
    fun `getKey should return current key when key ID matches`() {
        val currentKey = signingKeyProvider.getCurrentKey()
        val retrievedKey = signingKeyProvider.getKey(currentKey.keyId)

        assertNotNull(retrievedKey)
        assertEquals(currentKey.keyId, retrievedKey.keyId)
        assertEquals(currentKey.privateKey, retrievedKey.privateKey)
    }

    @Test
    fun `getKey should return null when key ID does not match`() {
        val retrievedKey = signingKeyProvider.getKey("invalid-key-id")

        assertNull(retrievedKey)
    }

    @Test
    fun `rotateKey should generate a new key pair`() {
        val oldKey = signingKeyProvider.getCurrentKey()

        signingKeyProvider.rotateKey()

        val newKey = signingKeyProvider.getCurrentKey()

        // Distinct cryptographic material AND a distinct kid (the
        // 8-hex randomized suffix guarantees rotation produces a new id
        // even within the same calendar month — see generateKeyId).
        assertNotEquals(oldKey.keyId, newKey.keyId)
        assertNotEquals(oldKey.privateKey, newKey.privateKey)
        assertNotEquals(oldKey.publicKey, newKey.publicKey)
    }

    @Test
    fun `rotateKey should maintain RS256 algorithm`() {
        signingKeyProvider.rotateKey()
        val newKey = signingKeyProvider.getCurrentKey()

        assertEquals("RS256", newKey.algorithm)
    }

    @Test
    fun `RSA key should be 2048 bits`() {
        val key = signingKeyProvider.getCurrentKey()

        // RSA key modulus length indicates key size
        val modulus = (key.publicKey as java.security.interfaces.RSAPublicKey).modulus
        assertTrue(modulus.bitLength() >= 2048)
    }
}
