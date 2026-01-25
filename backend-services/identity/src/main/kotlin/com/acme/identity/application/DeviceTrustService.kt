package com.acme.identity.application

import com.acme.identity.config.DeviceTrustConfig
import com.acme.identity.domain.DeviceTrust
import com.acme.identity.domain.events.DeviceRemembered
import com.acme.identity.domain.events.DeviceRevocationReason
import com.acme.identity.domain.events.DeviceRevoked
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.DeviceTrustRepository
import com.acme.identity.infrastructure.persistence.EventStoreRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Service for managing device trust for MFA bypass.
 *
 * Responsibilities:
 * - Create new device trusts with automatic TTL
 * - Verify device trust validity (fingerprint, user agent, expiry)
 * - Enforce concurrent device trust limits (max 10 per user)
 * - Evict oldest device trusts when limit is exceeded
 * - Revoke individual or all device trusts
 * - Publish DeviceRemembered and DeviceRevoked events
 *
 * Device trusts are stored in Redis with automatic expiration after 30 days.
 * When a user exceeds the maximum trusted devices (10), the oldest
 * device trust is automatically evicted to make room for the new one.
 *
 * @property deviceTrustRepository Repository for device trust storage in Redis.
 * @property eventStoreRepository Repository for persisting events to the event store.
 * @property eventPublisher Publisher for domain events.
 * @property config Device trust configuration (max devices, TTL).
 */
@Service
class DeviceTrustService(
    private val deviceTrustRepository: DeviceTrustRepository,
    private val eventStoreRepository: EventStoreRepository,
    private val eventPublisher: UserEventPublisher,
    private val config: DeviceTrustConfig
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Creates a new device trust for the user.
     *
     * Steps:
     * 1. Enforce concurrent device trust limit (evict oldest if needed)
     * 2. Create and save the new device trust
     * 3. Publish DeviceRemembered event
     *
     * @param userId The user's unique ID.
     * @param deviceFingerprint The device fingerprint.
     * @param userAgent The client's User-Agent header.
     * @param ipAddress The client's IP address.
     * @param correlationId Optional correlation ID for distributed tracing.
     * @return The created [DeviceTrust].
     */
    fun createTrust(
        userId: UUID,
        deviceFingerprint: String,
        userAgent: String,
        ipAddress: String,
        correlationId: UUID = UUID.randomUUID()
    ): DeviceTrust {
        logger.debug("Creating device trust for user $userId")

        // Enforce concurrent device trust limit
        evictOldestDeviceIfNeeded(userId, correlationId)

        // Create new device trust
        val deviceTrust = DeviceTrust.create(
            userId = userId,
            deviceFingerprint = deviceFingerprint,
            userAgent = userAgent,
            ipAddress = ipAddress,
            ttlSeconds = config.ttlSeconds
        )

        // Save to Redis
        deviceTrustRepository.save(deviceTrust)
        logger.info("Created device trust ${deviceTrust.id} for user $userId")

        // Persist and publish event
        val event = DeviceRemembered.create(
            userId = userId,
            deviceTrustId = deviceTrust.id,
            deviceFingerprint = deviceTrust.deviceFingerprint,
            userAgent = userAgent,
            ipAddress = ipAddress,
            trustedUntil = deviceTrust.expiresAt,
            correlationId = correlationId
        )
        eventStoreRepository.append(event)
        eventPublisher.publishDeviceRemembered(event)

        return deviceTrust
    }

    /**
     * Verifies a device trust token for MFA bypass.
     *
     * Checks:
     * 1. Device trust exists in Redis
     * 2. Device fingerprint matches (SHA-256 comparison)
     * 3. User agent matches exactly
     * 4. Trust has not expired
     *
     * If all checks pass, updates lastUsedAt timestamp and returns the trust.
     * If any check fails, returns null (silently fail to prevent information leakage).
     *
     * @param deviceTrustToken The device trust token from the cookie.
     * @param userId The user's unique ID.
     * @param deviceFingerprint The current device fingerprint.
     * @param userAgent The current User-Agent header.
     * @return The verified [DeviceTrust] if valid, null otherwise.
     */
    fun verifyTrust(
        deviceTrustToken: String,
        userId: UUID,
        deviceFingerprint: String,
        userAgent: String
    ): DeviceTrust? {
        logger.debug("Verifying device trust token for user $userId")

        // Find device trust
        val deviceTrust = deviceTrustRepository.findById(deviceTrustToken).orElse(null)
        if (deviceTrust == null) {
            logger.debug("Device trust token not found: $deviceTrustToken")
            return null
        }

        // Verify user ID matches
        if (deviceTrust.userId != userId) {
            logger.warn("Device trust user ID mismatch: expected $userId, got ${deviceTrust.userId}")
            return null
        }

        // Verify fingerprint and user agent match
        if (!deviceTrust.matches(deviceFingerprint, userAgent)) {
            logger.debug("Device trust fingerprint or user agent mismatch for token $deviceTrustToken")
            return null
        }

        // Verify not expired
        if (deviceTrust.isExpired()) {
            logger.debug("Device trust expired for token $deviceTrustToken")
            return null
        }

        // Update last used timestamp using copy() for data class immutability
        val updatedDeviceTrust = deviceTrust.copy(lastUsedAt = Instant.now())
        deviceTrustRepository.save(updatedDeviceTrust)

        logger.info("Device trust verified successfully for user $userId")
        return updatedDeviceTrust
    }

    /**
     * Lists all active device trusts for a user.
     *
     * Returns non-expired device trusts sorted by creation date (newest first).
     * Used for device management UI.
     *
     * @param userId The user's unique ID.
     * @return A list of active device trusts.
     */
    fun listDevices(userId: UUID): List<DeviceTrust> {
        val devices = deviceTrustRepository.findByUserId(userId)
            .filter { !it.isExpired() }
            .sortedByDescending { it.createdAt }

        logger.debug("Found ${devices.size} active device trusts for user $userId")
        return devices
    }

    /**
     * Revokes a specific device trust.
     *
     * Steps:
     * 1. Verify device trust belongs to the user
     * 2. Delete from Redis
     * 3. Publish DeviceRevoked event
     *
     * @param deviceTrustId The device trust ID to revoke.
     * @param userId The user's unique ID.
     * @param reason The reason for revocation.
     * @param correlationId Optional correlation ID for distributed tracing.
     * @return True if revoked, false if not found or not owned by user.
     */
    fun revokeDevice(
        deviceTrustId: String,
        userId: UUID,
        reason: DeviceRevocationReason = DeviceRevocationReason.USER_REVOKED,
        correlationId: UUID = UUID.randomUUID()
    ): Boolean {
        logger.info("Revoking device trust $deviceTrustId for user $userId (reason: $reason)")

        // Find and verify ownership
        // Note: findByIdAndUserId() is not supported by Spring Data Redis, so we use findById and verify userId
        val deviceTrust = deviceTrustRepository.findById(deviceTrustId).orElse(null)
        if (deviceTrust == null) {
            logger.warn("Device trust not found: $deviceTrustId")
            return false
        }

        // Verify ownership
        if (deviceTrust.userId != userId) {
            logger.warn("Device trust $deviceTrustId does not belong to user $userId (belongs to ${deviceTrust.userId})")
            return false
        }

        // Delete the device trust
        deviceTrustRepository.delete(deviceTrust)

        // Persist and publish event
        val event = DeviceRevoked.create(
            userId = userId,
            deviceTrustId = deviceTrustId,
            reason = reason,
            correlationId = correlationId
        )
        eventStoreRepository.append(event)
        eventPublisher.publishDeviceRevoked(event)

        logger.info("Revoked device trust $deviceTrustId for user $userId")
        return true
    }

    /**
     * Revokes all device trusts for a user.
     *
     * Used for:
     * - Password change (security measure)
     * - User-initiated "revoke all devices"
     * - Admin/security operations
     *
     * Publishes a DeviceRevoked event for each revoked device.
     *
     * @param userId The user's unique ID.
     * @param reason The reason for revocation.
     * @param correlationId Optional correlation ID for distributed tracing.
     * @return The number of devices revoked.
     */
    fun revokeAllDevices(
        userId: UUID,
        reason: DeviceRevocationReason = DeviceRevocationReason.USER_REVOKED_ALL,
        correlationId: UUID = UUID.randomUUID()
    ): Int {
        logger.info("Revoking all device trusts for user $userId (reason: $reason)")

        val devices = deviceTrustRepository.findByUserId(userId)

        devices.forEach { device ->
            // Delete the device trust
            deviceTrustRepository.delete(device)

            // Persist and publish event
            val event = DeviceRevoked.create(
                userId = userId,
                deviceTrustId = device.id,
                reason = reason,
                correlationId = correlationId
            )
            eventStoreRepository.append(event)
            eventPublisher.publishDeviceRevoked(event)
        }

        logger.info("Revoked ${devices.size} device trusts for user $userId")
        return devices.size
    }

    /**
     * Enforces the concurrent device trust limit for a user.
     *
     * If the user already has the maximum number of device trusts (10),
     * the oldest device trust is evicted to make room for a new one.
     *
     * A DeviceRevoked event is published for the evicted device.
     *
     * Note: This implementation has a known race condition where concurrent
     * device trust creations may temporarily exceed the limit. The check-then-act
     * pattern is not atomic. For production, this should use Redis Lua scripts
     * or distributed locking to ensure atomicity.
     *
     * @param userId The user's unique ID.
     * @param correlationId Correlation ID for distributed tracing.
     */
    private fun evictOldestDeviceIfNeeded(userId: UUID, correlationId: UUID) {
        val devices = deviceTrustRepository.findByUserId(userId)

        if (devices.size >= config.maxDevicesPerUser) {
            val oldest = devices.minByOrNull { it.createdAt }
                ?: run {
                    logger.error("Device trust list is not empty but minByOrNull returned null for user $userId")
                    return
                }

            logger.info("Evicting oldest device trust ${oldest.id} for user $userId (limit: ${config.maxDevicesPerUser})")

            // Delete the device trust
            deviceTrustRepository.delete(oldest)

            // Persist and publish DeviceRevoked event
            val event = DeviceRevoked.create(
                userId = userId,
                deviceTrustId = oldest.id,
                reason = DeviceRevocationReason.LIMIT_EXCEEDED,
                correlationId = correlationId
            )
            eventStoreRepository.append(event)
            eventPublisher.publishDeviceRevoked(event)
        }
    }
}
