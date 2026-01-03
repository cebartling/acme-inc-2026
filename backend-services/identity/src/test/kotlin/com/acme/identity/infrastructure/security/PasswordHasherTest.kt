package com.acme.identity.infrastructure.security

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class PasswordHasherTest {

    private lateinit var passwordHasher: PasswordHasher

    @BeforeEach
    fun setUp() {
        // Using the specified Argon2id parameters from the user story
        passwordHasher = PasswordHasher(
            memory = 65536,    // 64 MB
            iterations = 3,
            parallelism = 4,
            hashLength = 32
        )
    }

    @Test
    fun `hash should produce a non-empty hash`() {
        val password = "SecureP@ss123"
        val hash = passwordHasher.hash(password)

        assertTrue(hash.isNotBlank())
    }

    @Test
    fun `hash should produce different hashes for same password due to salt`() {
        val password = "SecureP@ss123"
        val hash1 = passwordHasher.hash(password)
        val hash2 = passwordHasher.hash(password)

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `verify should return true for correct password`() {
        val password = "SecureP@ss123"
        val hash = passwordHasher.hash(password)

        assertTrue(passwordHasher.verify(password, hash))
    }

    @Test
    fun `verify should return false for incorrect password`() {
        val password = "SecureP@ss123"
        val hash = passwordHasher.hash(password)

        assertFalse(passwordHasher.verify("WrongPassword123!", hash))
    }

    @Test
    fun `hash should contain argon2id identifier`() {
        val password = "SecureP@ss123"
        val hash = passwordHasher.hash(password)

        assertTrue(hash.contains("\$argon2id\$"))
    }

    @Test
    fun `verify should be case sensitive`() {
        val password = "SecureP@ss123"
        val hash = passwordHasher.hash(password)

        assertFalse(passwordHasher.verify("securep@ss123", hash))
    }
}
