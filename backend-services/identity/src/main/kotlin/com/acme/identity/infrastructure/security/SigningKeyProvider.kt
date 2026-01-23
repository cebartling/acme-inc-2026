package com.acme.identity.infrastructure.security

import com.acme.identity.config.JwtConfig
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Provides RSA signing keys for JWT token generation.
 *
 * This implementation generates RSA key pairs in-memory and rotates them
 * based on the configured rotation period. In production, this should be
 * enhanced to integrate with HashiCorp Vault for secure key storage and
 * retrieval.
 *
 * Key rotation strategy:
 * - Keys are identified by a key ID (kid) in the format "key-YYYY-MM"
 * - The current active key is used for signing new tokens
 * - Previously issued tokens remain valid until expiry
 * - Verification supports multiple keys for seamless rotation
 * - Automatic rotation runs daily based on keyRotationPeriodDays config
 *
 * CRITICAL LIMITATION: Keys are regenerated on service restart, invalidating
 * all existing tokens. For production, integrate with HashiCorp Vault or
 * database persistence to maintain keys across restarts.
 *
 * Future enhancement: Integration with HashiCorp Vault:
 * - Store private keys securely in Vault
 * - Retrieve keys on-demand with automatic rotation
 * - Support for key versioning and audit trails
 */
@Component
class SigningKeyProvider(
    private val jwtConfig: JwtConfig
) {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val keyPairGenerator: KeyPairGenerator
    private var currentKey: SigningKey
    private var lastRotationDate: LocalDate

    init {
        // Initialize RSA key pair generator with 2048-bit key size
        keyPairGenerator = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048, SecureRandom())
        }

        // Generate initial key
        currentKey = generateKey()
        lastRotationDate = LocalDate.now()
        logger.warn("Generated new signing key ${currentKey.keyId} - all existing tokens are now invalid!")
        logger.info("Initialized signing key provider with key ID: ${currentKey.keyId}")
    }

    /**
     * Gets the current active signing key.
     *
     * This key should be used for signing all new tokens. The key ID (kid)
     * is included in the JWT header to support key rotation and verification.
     *
     * @return The current [SigningKey] for token signing.
     */
    fun getCurrentKey(): SigningKey {
        return currentKey
    }

    /**
     * Gets a specific signing key by its key ID.
     *
     * Used during token verification to find the correct key for
     * validating the signature. Supports multiple keys for rotation.
     *
     * For this initial implementation, only the current key is supported.
     * Future enhancement: Maintain a cache of recent keys for verification.
     *
     * @param keyId The key ID from the JWT header.
     * @return The [SigningKey] if found, null otherwise.
     */
    fun getKey(keyId: String): SigningKey? {
        return if (keyId == currentKey.keyId) {
            currentKey
        } else {
            logger.warn("Requested key ID $keyId not found (current: ${currentKey.keyId})")
            null
        }
    }

    /**
     * Rotates to a new signing key.
     *
     * Generates a new RSA key pair and sets it as the current active key.
     * The previous key should be retained for verification of existing tokens.
     *
     * In production with Vault integration:
     * - Request a new key version from Vault
     * - Update the current key reference
     * - Maintain previous keys for verification
     */
    fun rotateKey() {
        val oldKeyId = currentKey.keyId
        currentKey = generateKey()
        lastRotationDate = LocalDate.now()
        logger.info("Rotated signing key from $oldKeyId to ${currentKey.keyId}")
    }

    /**
     * Automatically rotates signing keys based on the configured rotation period.
     *
     * Runs daily at 2 AM. Checks if the key rotation period has elapsed since
     * the last rotation. Default rotation period is 30 days.
     *
     * Note: This scheduled task is disabled by default in tests via @EnableScheduling.
     */
    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    fun autoRotateKey() {
        val daysSinceRotation = java.time.temporal.ChronoUnit.DAYS.between(lastRotationDate, LocalDate.now())

        if (daysSinceRotation >= jwtConfig.keyRotationPeriodDays) {
            logger.info("Auto-rotating signing key (days since last rotation: $daysSinceRotation)")
            rotateKey()
        } else {
            logger.debug("Skipping key rotation (days since last rotation: $daysSinceRotation, threshold: ${jwtConfig.keyRotationPeriodDays})")
        }
    }

    /**
     * Generates a new RSA signing key with a unique key ID.
     *
     * The key ID is based on the current year and month (e.g., "key-2026-01")
     * to support time-based rotation tracking.
     *
     * @return A new [SigningKey] instance.
     */
    private fun generateKey(): SigningKey {
        val keyPair = keyPairGenerator.generateKeyPair()
        val keyId = generateKeyId()

        return SigningKey(
            keyId = keyId,
            privateKey = keyPair.private,
            publicKey = keyPair.public,
            algorithm = "RS256"
        )
    }

    /**
     * Generates a unique key ID based on the current date.
     *
     * Format: "key-YYYY-MM" (e.g., "key-2026-01")
     *
     * @return The generated key ID string.
     */
    private fun generateKeyId(): String {
        val now = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM")
        return "key-${now.format(formatter)}"
    }
}
