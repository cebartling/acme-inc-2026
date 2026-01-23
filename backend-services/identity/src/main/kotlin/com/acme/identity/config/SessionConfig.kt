package com.acme.identity.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Configuration properties for session management.
 *
 * Maps to the `identity.session` configuration namespace in application.yml.
 *
 * @property maxPerUser Maximum number of concurrent sessions allowed per user.
 * @property ttlDays Session time-to-live in days.
 */
@Configuration
@ConfigurationProperties(prefix = "identity.session")
data class SessionConfig(
    var maxPerUser: Int = 5,
    var ttlDays: Long = 7
) {
    /**
     * Gets the session TTL in seconds for Redis.
     */
    val ttlSeconds: Long
        get() = ttlDays * 24 * 60 * 60
}
