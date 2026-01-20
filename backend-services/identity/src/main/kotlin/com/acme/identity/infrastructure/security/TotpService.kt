package com.acme.identity.infrastructure.security

import dev.samstevens.totp.code.CodeGenerator
import dev.samstevens.totp.code.CodeVerifier
import dev.samstevens.totp.code.DefaultCodeGenerator
import dev.samstevens.totp.code.DefaultCodeVerifier
import dev.samstevens.totp.code.HashingAlgorithm
import dev.samstevens.totp.secret.DefaultSecretGenerator
import dev.samstevens.totp.secret.SecretGenerator
import dev.samstevens.totp.time.SystemTimeProvider
import dev.samstevens.totp.time.TimeProvider
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant

/**
 * Service for TOTP (Time-based One-Time Password) operations.
 *
 * Implements RFC 6238 TOTP with the following configuration:
 * - Algorithm: SHA-1
 * - Digits: 6
 * - Time step: 30 seconds
 * - Time tolerance: ±1 step (allows codes from 30 seconds before/after)
 *
 * This service handles:
 * - Secret generation for new TOTP setup
 * - TOTP code verification with time tolerance
 * - Code hashing for replay prevention
 */
@Service
class TotpService {
    private val timeProvider: TimeProvider = SystemTimeProvider()
    private val codeGenerator: CodeGenerator = DefaultCodeGenerator(HashingAlgorithm.SHA1, TOTP_DIGITS)
    private val secretGenerator: SecretGenerator = DefaultSecretGenerator(SECRET_LENGTH)
    private val codeVerifier: CodeVerifier

    init {
        val verifier = DefaultCodeVerifier(codeGenerator, timeProvider)
        verifier.setTimePeriod(TIME_STEP_SECONDS)
        verifier.setAllowedTimePeriodDiscrepancy(TIME_TOLERANCE_STEPS)
        codeVerifier = verifier
    }

    /**
     * Generates a new TOTP secret for a user.
     *
     * The secret should be stored securely (encrypted at rest) and
     * presented to the user as a QR code for authenticator app setup.
     *
     * @return A new base32-encoded TOTP secret.
     */
    fun generateSecret(): String {
        return secretGenerator.generate()
    }

    /**
     * Verifies a TOTP code against a secret.
     *
     * Allows codes from the current time step and ±1 adjacent steps
     * to accommodate clock drift between server and authenticator app.
     *
     * @param secret The user's TOTP secret.
     * @param code The 6-digit code to verify.
     * @return `true` if the code is valid, `false` otherwise.
     */
    fun verifyCode(secret: String, code: String): Boolean {
        if (code.length != TOTP_DIGITS || !code.all { it.isDigit() }) {
            return false
        }
        return codeVerifier.isValidCode(secret, code)
    }

    /**
     * Gets the current time step for TOTP calculation.
     *
     * The time step is the number of 30-second intervals since the Unix epoch.
     *
     * @return The current time step.
     */
    fun getCurrentTimeStep(): Long {
        return Instant.now().epochSecond / TIME_STEP_SECONDS
    }

    /**
     * Generates a SHA-256 hash of a TOTP code.
     *
     * Used for storing used codes without keeping the actual code value.
     *
     * @param code The code to hash.
     * @return The hex-encoded SHA-256 hash.
     */
    fun hashCode(code: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(code.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generates a TOTP code for a given secret and time.
     *
     * Primarily used for testing. In production, codes are generated
     * by the user's authenticator app.
     *
     * @param secret The TOTP secret.
     * @param timeStep The time step to generate the code for (defaults to current).
     * @return The 6-digit TOTP code.
     */
    fun generateCode(secret: String, timeStep: Long = getCurrentTimeStep()): String {
        return codeGenerator.generate(secret, timeStep)
    }

    companion object {
        /** TOTP algorithm as per RFC 6238. */
        const val ALGORITHM = "SHA1"

        /** Number of digits in the code. */
        const val TOTP_DIGITS = 6

        /** Time step in seconds. */
        const val TIME_STEP_SECONDS = 30

        /** Time tolerance in steps (±1 = 30 seconds each way). */
        const val TIME_TOLERANCE_STEPS = 1

        /** Length of generated secrets in characters. */
        const val SECRET_LENGTH = 32
    }
}
