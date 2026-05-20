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
    @Volatile
    private var currentKey: SigningKey
    @Volatile
    private var lastRotationDate: LocalDate

    /**
     * Bounded LRU of previously-active signing keys, keyed by kid. Retained
     * so refresh / access tokens issued before a recent rotation can still
     * be verified by their JWT `kid` header. Synchronized on [previousKeys]
     * for the rotation path; reads from [getKey] are safe because
     * LinkedHashMap reads are themselves single-pointer lookups and the
     * map is only mutated under the same lock during rotation.
     */
    private val previousKeys: LinkedHashMap<String, SigningKey> = LinkedHashMap()

    companion object {
        /**
         * Number of recent (rotated-out) signing keys to retain for
         * verification. Tokens signed under any of these still verify;
         * older keys are evicted on rotation. Chosen so a 7-day refresh
         * token survives ~PREVIOUS_KEY_CAPACITY rotations.
         */
        const val PREVIOUS_KEY_CAPACITY = 3
    }

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
     * Gets a signing key by its key ID, checking the current key first and
     * then the retained previous keys. Used during token verification so
     * tokens issued before a recent key rotation still validate.
     *
     * @param keyId The key ID from the JWT header.
     * @return The [SigningKey] if found, null otherwise.
     */
    fun getKey(keyId: String): SigningKey? {
        if (keyId == currentKey.keyId) return currentKey
        synchronized(previousKeys) {
            val previous = previousKeys[keyId]
            if (previous != null) return previous
        }
        logger.warn(
            "Requested key ID $keyId not found (current: ${currentKey.keyId}, " +
                    "previous: ${previousKeys.keys})"
        )
        return null
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
        val oldKey = currentKey
        val newKey = generateKey()
        // generateKeyId() includes a randomized hex suffix so two rotations
        // within the same calendar month produce distinct kids — duplicate
        // kids would shadow the outgoing key in previousKeys, so the
        // uniqueness is a load-bearing precondition here, not just a tidy
        // human convention.
        check(newKey.keyId != oldKey.keyId) {
            "rotateKey produced a duplicate kid: ${oldKey.keyId}"
        }
        synchronized(previousKeys) {
            previousKeys[oldKey.keyId] = oldKey
            // LinkedHashMap is insertion-ordered, so the oldest key is the
            // first entry. Evict to keep the retained set bounded.
            while (previousKeys.size > PREVIOUS_KEY_CAPACITY) {
                val evicted = previousKeys.keys.iterator().next()
                previousKeys.remove(evicted)
                logger.info("Evicted previous signing key {} from verification cache", evicted)
            }
        }
        currentKey = newKey
        lastRotationDate = LocalDate.now()
        logger.info("Rotated signing key from ${oldKey.keyId} to ${currentKey.keyId}")
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
     * Generates a unique key ID based on the current date plus a short
     * randomized suffix so two rotations within the same calendar month
     * still produce distinct identifiers. Without the suffix, a same-month
     * rotation would silently shadow the outgoing kid in [previousKeys]
     * (defensive branch in [rotateKey]) and tokens signed under the old
     * material would fail verification.
     *
     * Format: "key-YYYY-MM-XXXXXXXX" (e.g., "key-2026-01-a1b2c3d4")
     *
     * @return The generated key ID string.
     */
    private fun generateKeyId(): String {
        val now = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM")
        val suffix = java.lang.Long.toHexString(SecureRandom().nextLong())
            .padStart(16, '0')
            .substring(0, 8)
        return "key-${now.format(formatter)}-$suffix"
    }
}
