package com.acme.identity.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Configuration properties for device trust management.
 *
 * Maps to the `identity.device-trust` configuration namespace in application.yml.
 *
 * @property maxDevicesPerUser Maximum number of trusted devices allowed per user.
 * @property ttlDays Device trust time-to-live in days.
 */
@Configuration
@ConfigurationProperties(prefix = "identity.device-trust")
data class DeviceTrustConfig(
    var maxDevicesPerUser: Int = 10,
    var ttlDays: Long = 30
) {
    /**
     * Gets the device trust TTL in seconds for Redis.
     */
    val ttlSeconds: Long
        get() = ttlDays * 24 * 60 * 60
}
