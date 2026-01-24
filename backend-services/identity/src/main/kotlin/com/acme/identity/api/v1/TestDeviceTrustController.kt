package com.acme.identity.api.v1

import com.acme.identity.api.v1.dto.*
import com.acme.identity.application.DeviceTrustService
import com.acme.identity.domain.DeviceTrust
import com.acme.identity.infrastructure.persistence.DeviceTrustRepository
import com.acme.identity.infrastructure.persistence.EventStoreRepository
import jakarta.validation.Valid
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * Test-only API endpoints for device trust management.
 *
 * These endpoints are used exclusively by acceptance tests to set up
 * test scenarios and verify device trust behavior. They bypass normal
 * authentication and authorization to allow tests to manipulate device
 * trust state directly.
 *
 * IMPORTANT: This controller is only enabled in test profiles. It should
 * never be exposed in production environments.
 */
@RestController
@RequestMapping("/api/v1/test")
@ConditionalOnProperty(name = ["acme.test-api.enabled"], havingValue = "true", matchIfMissing = false)
class TestDeviceTrustController(
    private val deviceTrustService: DeviceTrustService,
    private val deviceTrustRepository: DeviceTrustRepository,
    private val eventStoreRepository: EventStoreRepository
) {

    /**
     * Creates a device trust for testing purposes.
     *
     * This endpoint allows tests to create device trusts with custom parameters
     * such as TTL, creation date, and device trust ID. This is useful for setting
     * up various test scenarios (expired trusts, old trusts, etc.).
     *
     * @param userId The user ID to create the device trust for.
     * @param request The device trust creation request.
     * @return The created device trust ID.
     */
    @PostMapping("/users/{userId}/device-trusts")
    fun createDeviceTrust(
        @PathVariable userId: UUID,
        @Valid @RequestBody request: CreateTestDeviceTrustRequest
    ): ResponseEntity<CreateTestDeviceTrustResponse> {
        val correlationId = UUID.randomUUID()

        // Create the device trust with default parameters first
        val deviceTrust = deviceTrustService.createTrust(
            userId = userId,
            deviceFingerprint = request.deviceFingerprint,
            userAgent = request.userAgent,
            ipAddress = request.ipAddress,
            correlationId = correlationId
        )

        // If custom parameters provided, modify the device trust
        val modifiedDeviceTrust = if (request.ttlSeconds != null || request.deviceTrustId != null || request.createdDaysAgo != null) {
            val customId = request.deviceTrustId ?: deviceTrust.id
            val customTtl = request.ttlSeconds ?: deviceTrust.ttl
            val createdAt = if (request.createdDaysAgo != null) {
                Instant.now().minus(request.createdDaysAgo.toLong(), ChronoUnit.DAYS)
            } else {
                deviceTrust.createdAt
            }
            val expiresAt = createdAt.plusSeconds(customTtl)
            val lastUsedAt = createdAt

            val customDeviceTrust = deviceTrust.copy(
                id = customId,
                ttl = customTtl,
                createdAt = createdAt,
                expiresAt = expiresAt,
                lastUsedAt = lastUsedAt
            )

            // Delete the original if ID changed, then save the custom one
            if (customId != deviceTrust.id) {
                deviceTrustRepository.deleteById(deviceTrust.id)
            }
            deviceTrustRepository.save(customDeviceTrust)

            customDeviceTrust
        } else {
            deviceTrust
        }

        return ResponseEntity.ok(CreateTestDeviceTrustResponse(modifiedDeviceTrust.id))
    }

    /**
     * Retrieves all device trusts for a user.
     *
     * @param userId The user ID to query.
     * @return List of device trusts.
     */
    @GetMapping("/users/{userId}/device-trusts")
    fun getDeviceTrusts(
        @PathVariable userId: UUID
    ): ResponseEntity<TestDeviceTrustsResponse> {
        val deviceTrusts = deviceTrustRepository.findByUserId(userId)

        val devices = deviceTrusts.map { trust ->
            TestDeviceTrustInfo(
                id = trust.id,
                userId = trust.userId.toString(),
                deviceFingerprint = trust.deviceFingerprint,
                userAgent = trust.userAgent,
                ipAddress = trust.ipAddress,
                createdAt = trust.createdAt.toString(),
                expiresAt = trust.expiresAt.toString(),
                lastUsedAt = trust.lastUsedAt.toString(),
                ttl = trust.ttl
            )
        }

        return ResponseEntity.ok(TestDeviceTrustsResponse(devices))
    }

    /**
     * Retrieves a single device trust by ID.
     *
     * @param deviceTrustId The device trust ID to query.
     * @return The device trust, or 404 if not found.
     */
    @GetMapping("/device-trusts/{deviceTrustId}")
    fun getDeviceTrust(
        @PathVariable deviceTrustId: String
    ): ResponseEntity<TestDeviceTrustInfo> {
        val deviceTrust = deviceTrustRepository.findById(deviceTrustId)
            .orElse(null) ?: return ResponseEntity.notFound().build()

        val info = TestDeviceTrustInfo(
            id = deviceTrust.id,
            userId = deviceTrust.userId.toString(),
            deviceFingerprint = deviceTrust.deviceFingerprint,
            userAgent = deviceTrust.userAgent,
            ipAddress = deviceTrust.ipAddress,
            createdAt = deviceTrust.createdAt.toString(),
            expiresAt = deviceTrust.expiresAt.toString(),
            lastUsedAt = deviceTrust.lastUsedAt.toString(),
            ttl = deviceTrust.ttl
        )

        return ResponseEntity.ok(info)
    }

    /**
     * Retrieves the TTL of a device trust.
     *
     * @param deviceTrustId The device trust ID to query.
     * @return The TTL in seconds, or 404 if not found.
     */
    @GetMapping("/device-trusts/{deviceTrustId}/ttl")
    fun getDeviceTrustTtl(
        @PathVariable deviceTrustId: String
    ): ResponseEntity<DeviceTrustTtlResponse> {
        val deviceTrust = deviceTrustRepository.findById(deviceTrustId)
            .orElse(null) ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(DeviceTrustTtlResponse(deviceTrust.ttl))
    }

    /**
     * Retrieves DeviceRemembered events for a user.
     *
     * @param userId The user ID to query events for.
     * @return List of DeviceRemembered events.
     */
    @GetMapping("/events/DeviceRemembered")
    fun getDeviceRememberedEvents(
        @RequestParam userId: UUID?
    ): ResponseEntity<EventsResponse> {
        if (userId == null) {
            return ResponseEntity.badRequest().build()
        }

        val events = eventStoreRepository.findByEventTypeAndAggregateId("DeviceRemembered", userId)

        return ResponseEntity.ok(EventsResponse(events))
    }

    /**
     * Retrieves DeviceRevoked events for a user.
     *
     * @param userId The user ID to query events for.
     * @return List of DeviceRevoked events.
     */
    @GetMapping("/events/DeviceRevoked")
    fun getDeviceRevokedEvents(
        @RequestParam userId: UUID?
    ): ResponseEntity<EventsResponse> {
        if (userId == null) {
            return ResponseEntity.badRequest().build()
        }

        val events = eventStoreRepository.findByEventTypeAndAggregateId("DeviceRevoked", userId)

        return ResponseEntity.ok(EventsResponse(events))
    }
}
