package com.acme.customer.infrastructure.cache

import com.acme.customer.api.v1.dto.CustomerResponse
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

/**
 * Service for caching customer profiles in Redis.
 *
 * Provides methods to:
 * - Get cached customer profiles by userId
 * - Store customer profiles with TTL
 * - Invalidate cache entries when profiles are updated
 *
 * Cache entries use the key format: `customer:profile:{userId}`
 * TTL is set to 5 minutes to balance performance and freshness.
 */
@Service
class CustomerCacheService(
    private val redisTemplate: RedisTemplate<String, Any>
) {
    private val logger = LoggerFactory.getLogger(CustomerCacheService::class.java)

    companion object {
        private const val KEY_PREFIX = "customer:profile:"
        private val TTL = Duration.ofMinutes(5)
    }

    /**
     * Retrieves a customer profile from the cache.
     *
     * @param userId The user ID to look up.
     * @return The cached customer profile, or null if not found or expired.
     */
    fun get(userId: UUID): CustomerResponse? {
        return try {
            val key = "$KEY_PREFIX$userId"
            val cached = redisTemplate.opsForValue().get(key) as? CustomerResponse

            if (cached != null) {
                logger.debug("Cache hit for user: {}", userId)
            } else {
                logger.debug("Cache miss for user: {}", userId)
            }

            cached
        } catch (e: Exception) {
            logger.error("Failed to get cached profile for user {}: {}", userId, e.message, e)
            null // Return null on cache errors to fall back to database
        }
    }

    /**
     * Stores a customer profile in the cache with TTL.
     *
     * @param userId The user ID to use as the cache key.
     * @param profile The customer profile to cache.
     */
    fun put(userId: UUID, profile: CustomerResponse) {
        try {
            val key = "$KEY_PREFIX$userId"
            redisTemplate.opsForValue().set(key, profile, TTL)
            logger.debug("Cached profile for user: {} with TTL: {}", userId, TTL)
        } catch (e: Exception) {
            logger.error("Failed to cache profile for user {}: {}", userId, e.message, e)
            // Don't throw - caching is performance optimization, not critical
        }
    }

    /**
     * Invalidates a cached customer profile.
     *
     * This should be called when a customer profile or preferences are updated
     * to ensure the cache doesn't serve stale data.
     *
     * @param userId The user ID whose cache entry should be invalidated.
     */
    fun invalidate(userId: UUID) {
        try {
            val key = "$KEY_PREFIX$userId"
            val deleted = redisTemplate.delete(key)
            if (deleted) {
                logger.debug("Invalidated cache for user: {}", userId)
            } else {
                logger.debug("No cache entry to invalidate for user: {}", userId)
            }
        } catch (e: Exception) {
            logger.error("Failed to invalidate cache for user {}: {}", userId, e.message, e)
            // Don't throw - invalidation failure is not critical
        }
    }
}
