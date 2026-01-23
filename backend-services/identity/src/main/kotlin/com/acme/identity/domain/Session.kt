package com.acme.identity.domain

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import org.springframework.data.redis.core.TimeToLive
import org.springframework.data.redis.core.index.Indexed
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Session entity representing an authenticated user session.
 *
 * Sessions are stored in Redis with a TTL to automatically expire
 * after the configured duration. Each session is tied to a specific
 * user and device.
 *
 * @property id The unique session identifier (format: sess_{UUID}).
 * @property userId The ID of the user this session belongs to.
 * @property deviceId The device identifier (fingerprint or explicit device ID).
 * @property ipAddress The IP address of the client that created the session.
 * @property userAgent The User-Agent header from the client request.
 * @property tokenFamily The token family ID for refresh token rotation detection.
 * @property createdAt When the session was created.
 * @property expiresAt When the session expires.
 * @property ttl Time-to-live in seconds for Redis expiration.
 */
@RedisHash("sessions")
data class Session(
    @Id
    val id: String,

    @Indexed
    val userId: UUID,

    val deviceId: String,

    val ipAddress: String,

    val userAgent: String,

    val tokenFamily: String,

    val createdAt: Instant,

    val expiresAt: Instant,

    @TimeToLive(unit = TimeUnit.SECONDS)
    val ttl: Long
) {
    companion object {
        /**
         * Creates a new Session with the given parameters.
         *
         * @param userId The user's unique ID.
         * @param deviceId The device identifier.
         * @param ipAddress The client's IP address.
         * @param userAgent The client's User-Agent.
         * @param tokenFamily The token family for rotation.
         * @param ttlSeconds The session TTL in seconds (default: 7 days).
         * @return A new Session instance.
         */
        fun create(
            userId: UUID,
            deviceId: String,
            ipAddress: String,
            userAgent: String,
            tokenFamily: String,
            ttlSeconds: Long = 604800L // 7 days
        ): Session {
            val sessionId = "sess_${UUID.randomUUID()}"
            val now = Instant.now()
            val expiresAt = now.plusSeconds(ttlSeconds)

            return Session(
                id = sessionId,
                userId = userId,
                deviceId = deviceId,
                ipAddress = ipAddress,
                userAgent = userAgent,
                tokenFamily = tokenFamily,
                createdAt = now,
                expiresAt = expiresAt,
                ttl = ttlSeconds
            )
        }
    }
}
