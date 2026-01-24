package com.acme.identity.api.v1.dto

import jakarta.validation.constraints.NotBlank

/**
 * Request to create a device trust for testing purposes.
 *
 * This DTO is used only in the test API to set up device trust scenarios
 * for acceptance testing. It allows specifying custom TTL and creation dates.
 */
data class CreateTestDeviceTrustRequest(
    @field:NotBlank
    val deviceFingerprint: String,

    @field:NotBlank
    val userAgent: String,

    @field:NotBlank
    val ipAddress: String,

    /**
     * Optional custom TTL in seconds. If not provided, uses default (30 days).
     */
    val ttlSeconds: Long? = null,

    /**
     * Optional custom device trust ID. If not provided, a UUID is generated.
     */
    val deviceTrustId: String? = null,

    /**
     * Optional number of days in the past when device was created.
     * Used for testing scenarios with old device trusts.
     */
    val createdDaysAgo: Int? = null
)

/**
 * Response containing the created device trust ID.
 */
data class CreateTestDeviceTrustResponse(
    val deviceTrustId: String
)

/**
 * Response containing list of device trusts.
 */
data class TestDeviceTrustsResponse(
    val devices: List<TestDeviceTrustInfo>
)

/**
 * Device trust information for test API responses.
 */
data class TestDeviceTrustInfo(
    val id: String,
    val userId: String,
    val deviceFingerprint: String,
    val userAgent: String,
    val ipAddress: String,
    val createdAt: String,
    val expiresAt: String,
    val lastUsedAt: String,
    val ttl: Long
)

/**
 * Response containing TTL of a device trust.
 */
data class DeviceTrustTtlResponse(
    val ttl: Long
)

/**
 * Response containing list of events.
 */
data class EventsResponse(
    val events: List<Map<String, Any?>>
)
