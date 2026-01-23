package com.acme.identity.infrastructure.security

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

class SigningKeyProviderTest {

    private lateinit var signingKeyProvider: SigningKeyProvider

    @BeforeEach
    fun setUp() {
        signingKeyProvider = SigningKeyProvider()
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
    fun `key ID should follow format key-YYYY-MM`() {
        val key = signingKeyProvider.getCurrentKey()

        assertTrue(key.keyId.matches(Regex("key-\\d{4}-\\d{2}")))
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
    fun `rotateKey should generate a new key with different key ID`() {
        val oldKey = signingKeyProvider.getCurrentKey()
        val oldKeyId = oldKey.keyId

        // Rotate the key
        signingKeyProvider.rotateKey()

        val newKey = signingKeyProvider.getCurrentKey()

        assertNotEquals(oldKeyId, newKey.keyId)
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
