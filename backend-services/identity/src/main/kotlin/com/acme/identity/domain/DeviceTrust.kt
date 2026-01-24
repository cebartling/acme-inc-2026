package com.acme.identity.domain

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import org.springframework.data.redis.core.TimeToLive
import org.springframework.data.redis.core.index.Indexed
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Device trust entity representing a trusted device for MFA bypass.
 *
 * Device trusts are stored in Redis with a TTL to automatically expire
 * after 30 days. Each trust is tied to a specific user, device fingerprint,
 * and user agent string.
 *
 * Security considerations:
 * - Device fingerprint AND user agent must both match for trust to be valid
 * - IP address is logged but NOT enforced (mobile networks, VPNs change IPs)
 * - User agent validation prevents token theft when browser updates occur
 * - HttpOnly cookies prevent XSS attacks
 * - Maximum 10 trusted devices per user
 *
 * @property id The unique device trust identifier (format: trust_{UUID}).
 * @property userId The ID of the user this trust belongs to.
 * @property deviceFingerprint SHA-256 hash of the device fingerprint.
 * @property userAgent The User-Agent header from the client request.
 * @property ipAddress The IP address when trust was created (for audit only).
 * @property createdAt When the trust was created.
 * @property expiresAt When the trust expires.
 * @property lastUsedAt When the trust was last used for MFA bypass.
 * @property ttl Time-to-live in seconds for Redis expiration.
 */
@RedisHash("device_trusts")
data class DeviceTrust(
    @Id
    val id: String,

    @Indexed
    val userId: UUID,

    val deviceFingerprint: String,

    val userAgent: String,

    val ipAddress: String,

    val createdAt: Instant,

    val expiresAt: Instant,

    var lastUsedAt: Instant,

    @TimeToLive(unit = TimeUnit.SECONDS)
    val ttl: Long
) {
    companion object {
        /**
         * Creates a new DeviceTrust with the given parameters.
         *
         * @param userId The user's unique ID.
         * @param deviceFingerprint Raw device fingerprint (will be hashed).
         * @param userAgent The client's User-Agent.
         * @param ipAddress The client's IP address.
         * @param ttlSeconds The trust TTL in seconds (default: 30 days).
         * @return A new DeviceTrust instance.
         */
        fun create(
            userId: UUID,
            deviceFingerprint: String,
            userAgent: String,
            ipAddress: String,
            ttlSeconds: Long = 2592000L // 30 days
        ): DeviceTrust {
            val trustId = "trust_${UUID.randomUUID()}"
            val now = Instant.now()
            val expiresAt = now.plusSeconds(ttlSeconds)

            // Hash the device fingerprint with SHA-256
            val hashedFingerprint = hashFingerprint(deviceFingerprint)

            return DeviceTrust(
                id = trustId,
                userId = userId,
                deviceFingerprint = hashedFingerprint,
                userAgent = userAgent,
                ipAddress = ipAddress,
                createdAt = now,
                expiresAt = expiresAt,
                lastUsedAt = now,
                ttl = ttlSeconds
            )
        }

        /**
         * Hashes a device fingerprint using SHA-256.
         *
         * @param fingerprint The raw fingerprint string.
         * @return The SHA-256 hash as a hex string.
         */
        private fun hashFingerprint(fingerprint: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(fingerprint.toByteArray())
            return hashBytes.joinToString("") { "%02x".format(it) }
        }
    }

    /**
     * Checks if this device trust matches the provided fingerprint and user agent.
     *
     * @param fingerprint The raw device fingerprint to check.
     * @param userAgent The user agent to check.
     * @return True if both fingerprint and user agent match.
     */
    fun matches(fingerprint: String, userAgent: String): Boolean {
        val hashedFingerprint = MessageDigest.getInstance("SHA-256")
            .digest(fingerprint.toByteArray())
            .joinToString("") { "%02x".format(it) }

        return this.deviceFingerprint == hashedFingerprint && this.userAgent == userAgent
    }

    /**
     * Checks if this device trust has expired.
     *
     * @return True if the trust has expired.
     */
    fun isExpired(): Boolean {
        return expiresAt.isBefore(Instant.now())
    }

    /**
     * Updates the lastUsedAt timestamp to now.
     *
     * Called when the device trust is successfully used for MFA bypass.
     */
    fun touch() {
        lastUsedAt = Instant.now()
    }

    /**
     * Parses a human-readable device name from the user agent.
     *
     * Extracts browser and OS information for display purposes.
     *
     * @return A simplified device name (e.g., "Chrome on macOS").
     */
    fun parseDeviceName(): String {
        val browser = when {
            userAgent.contains("Chrome") -> "Chrome"
            userAgent.contains("Firefox") -> "Firefox"
            userAgent.contains("Safari") -> "Safari"
            userAgent.contains("Edge") -> "Edge"
            else -> "Unknown Browser"
        }

        // Check more specific patterns first (Android/iOS before Linux/macOS)
        val os = when {
            userAgent.contains("Android") -> "Android"
            userAgent.contains("iPhone") || userAgent.contains("iPad") -> "iOS"
            userAgent.contains("Mac OS X") || userAgent.contains("Macintosh") -> "macOS"
            userAgent.contains("Windows") -> "Windows"
            userAgent.contains("Linux") -> "Linux"
            else -> "Unknown OS"
        }

        return "$browser on $os"
    }
}
