package com.acme.identity.api.v1.dto

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

/**
 * Response DTO for a single trusted device.
 *
 * @property id The unique device trust identifier.
 * @property deviceName Human-readable device name (e.g., "Chrome on macOS").
 * @property createdAt When the device was first trusted.
 * @property lastUsedAt When the device was last used for MFA bypass.
 * @property expiresAt When the device trust expires.
 * @property ipAddress The IP address where the device was trusted.
 * @property isCurrent Whether this is the current device (based on cookie).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DeviceTrustInfo(
    val id: String,
    val deviceName: String,
    val createdAt: Instant,
    val lastUsedAt: Instant,
    val expiresAt: Instant,
    val ipAddress: String,
    val isCurrent: Boolean
)

/**
 * Response DTO for listing all trusted devices.
 *
 * @property devices List of trusted devices for the user.
 */
data class DevicesResponse(
    val devices: List<DeviceTrustInfo>
)

/**
 * Response DTO for device revocation errors.
 *
 * @property error The error code.
 * @property message Human-readable error message.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DeviceRevocationErrorResponse(
    val error: String,
    val message: String
)
