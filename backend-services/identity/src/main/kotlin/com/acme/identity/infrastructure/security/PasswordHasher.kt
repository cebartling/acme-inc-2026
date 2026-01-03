package com.acme.identity.infrastructure.security

import com.password4j.Argon2Function
import com.password4j.Password
import com.password4j.types.Argon2
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Service for securely hashing and verifying passwords using Argon2id.
 *
 * Argon2id is the recommended algorithm for password hashing, combining
 * resistance to both side-channel and GPU-based attacks. The parameters
 * are configurable via application properties.
 *
 * Default parameters (per OWASP recommendations):
 * - Memory: 64 MB (65536 KB)
 * - Iterations: 3
 * - Parallelism: 4 threads
 * - Hash length: 32 bytes
 *
 * @property memory Memory cost in KB. Higher values increase resistance to GPU attacks.
 * @property iterations Time cost (number of passes). Higher values increase CPU time.
 * @property parallelism Degree of parallelism. Should match available CPU cores.
 * @property hashLength Output hash length in bytes.
 */
@Component
class PasswordHasher(
    @Value("\${identity.password.argon2.memory:65536}")
    private val memory: Int,

    @Value("\${identity.password.argon2.iterations:3}")
    private val iterations: Int,

    @Value("\${identity.password.argon2.parallelism:4}")
    private val parallelism: Int,

    @Value("\${identity.password.argon2.hash-length:32}")
    private val hashLength: Int
) {
    private val argon2Function: Argon2Function by lazy {
        Argon2Function.getInstance(
            memory,
            iterations,
            parallelism,
            hashLength,
            Argon2.ID
        )
    }

    /**
     * Hashes a plain text password using Argon2id.
     *
     * The resulting hash includes the algorithm parameters and salt,
     * making it self-describing and suitable for storage.
     *
     * @param password The plain text password to hash.
     * @return The Argon2id hash string including parameters and salt.
     */
    fun hash(password: String): String {
        return Password.hash(password)
            .with(argon2Function)
            .result
    }

    /**
     * Verifies a plain text password against a stored hash.
     *
     * Uses constant-time comparison to prevent timing attacks.
     *
     * @param password The plain text password to verify.
     * @param hash The stored Argon2id hash to compare against.
     * @return `true` if the password matches, `false` otherwise.
     */
    fun verify(password: String, hash: String): Boolean {
        return Password.check(password, hash)
            .with(argon2Function)
    }
}
