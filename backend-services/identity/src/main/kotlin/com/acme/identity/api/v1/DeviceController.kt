package com.acme.identity.api.v1

import com.acme.identity.api.v1.dto.DeviceRevocationErrorResponse
import com.acme.identity.api.v1.dto.DeviceTrustInfo
import com.acme.identity.api.v1.dto.DevicesResponse
import com.acme.identity.application.DeviceTrustService
import com.acme.identity.application.TokenService
import com.acme.identity.domain.events.DeviceRevocationReason
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * REST controller for device trust management endpoints.
 *
 * Provides the public API for managing trusted devices that can bypass MFA.
 * All endpoints require authentication via access_token cookie.
 * All endpoints are versioned under `/api/v1/auth/devices`.
 *
 * @property deviceTrustService Service for device trust operations.
 * @property tokenService Service for parsing JWT tokens.
 */
@RestController
@RequestMapping("/api/v1/auth/devices")
class DeviceController(
    private val deviceTrustService: DeviceTrustService,
    private val tokenService: TokenService
) {
    private val logger = LoggerFactory.getLogger(DeviceController::class.java)

    /**
     * Lists all trusted devices for the authenticated user.
     *
     * Extracts the user ID from the access_token cookie JWT.
     * Returns devices sorted by creation date (newest first).
     *
     * @param accessToken The access token from the cookie.
     * @param deviceTrustCookie Optional current device trust cookie to mark the current device.
     * @return 200 OK with list of trusted devices,
     *         401 Unauthorized if access token is missing or invalid.
     */
    @GetMapping
    fun listDevices(
        @CookieValue(value = "access_token", required = false) accessToken: String?,
        @CookieValue(value = "device_trust", required = false) deviceTrustCookie: String?
    ): ResponseEntity<Any> {
        // Authenticate user
        val userId = authenticateRequest(accessToken)
            ?: return unauthorizedResponse("Authentication required")

        // Get all trusted devices for the user
        val devices = deviceTrustService.listDevices(userId)

        // Map to response DTOs
        val deviceInfos = devices.map { device ->
            DeviceTrustInfo(
                id = device.id,
                deviceName = device.parseDeviceName(),
                createdAt = device.createdAt,
                lastUsedAt = device.lastUsedAt,
                expiresAt = device.expiresAt,
                ipAddress = device.ipAddress,
                isCurrent = device.id == deviceTrustCookie
            )
        }

        logger.debug("Returning {} trusted devices for user {}", deviceInfos.size, userId)

        return ResponseEntity.ok(DevicesResponse(devices = deviceInfos))
    }

    /**
     * Revokes a specific trusted device.
     *
     * Only the device owner can revoke their own devices.
     * Returns 204 No Content on success.
     *
     * @param deviceId The device trust ID to revoke.
     * @param accessToken The access token from the cookie.
     * @return 204 No Content on success,
     *         401 Unauthorized if access token is missing or invalid,
     *         404 Not Found if device doesn't exist or doesn't belong to user.
     */
    @DeleteMapping("/{deviceId}")
    fun revokeDevice(
        @PathVariable deviceId: String,
        @CookieValue(value = "access_token", required = false) accessToken: String?
    ): ResponseEntity<Any> {
        // Authenticate user
        val userId = authenticateRequest(accessToken)
            ?: return unauthorizedResponse("Authentication required")

        logger.info("User {} attempting to revoke device {}", userId, deviceId)

        // Revoke the device
        val revoked = deviceTrustService.revokeDevice(
            deviceTrustId = deviceId,
            userId = userId,
            reason = DeviceRevocationReason.USER_REVOKED
        )

        return if (revoked) {
            logger.info("Successfully revoked device {} for user {}", deviceId, userId)
            ResponseEntity.noContent().build()
        } else {
            logger.warn("Device {} not found or not owned by user {}", deviceId, userId)
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                DeviceRevocationErrorResponse(
                    error = "DEVICE_NOT_FOUND",
                    message = "Device not found or access denied"
                )
            )
        }
    }

    /**
     * Revokes all trusted devices for the authenticated user.
     *
     * This will require the user to complete MFA on all devices on next signin.
     * Returns 204 No Content on success.
     *
     * @param accessToken The access token from the cookie.
     * @return 204 No Content on success,
     *         401 Unauthorized if access token is missing or invalid.
     */
    @DeleteMapping
    fun revokeAllDevices(
        @CookieValue(value = "access_token", required = false) accessToken: String?
    ): ResponseEntity<Any> {
        // Authenticate user
        val userId = authenticateRequest(accessToken)
            ?: return unauthorizedResponse("Authentication required")

        logger.info("User {} revoking all trusted devices", userId)

        // Revoke all devices
        val revokedCount = deviceTrustService.revokeAllDevices(
            userId = userId,
            reason = DeviceRevocationReason.USER_REVOKED_ALL
        )

        logger.info("Revoked {} devices for user {}", revokedCount, userId)

        return ResponseEntity.noContent().build()
    }

    /**
     * Authenticates a request by validating the access token.
     *
     * @param accessToken The JWT access token from the cookie.
     * @return The authenticated user ID, or null if authentication fails.
     */
    private fun authenticateRequest(accessToken: String?): UUID? {
        if (accessToken.isNullOrBlank()) {
            logger.debug("Missing access token in request")
            return null
        }

        return tokenService.parseAccessToken(accessToken)
    }

    /**
     * Creates a 401 Unauthorized response with error details.
     *
     * @param message The error message.
     * @return ResponseEntity with 401 status and error body.
     */
    private fun unauthorizedResponse(message: String): ResponseEntity<Any> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            DeviceRevocationErrorResponse(
                error = "UNAUTHORIZED",
                message = message
            )
        )
    }
}
