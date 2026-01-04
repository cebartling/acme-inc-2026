package com.acme.identity.infrastructure.security

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * In-memory rate limiter using the token bucket algorithm.
 *
 * Implements per-key rate limiting to prevent abuse of registration
 * and other sensitive endpoints. Each key (typically an IP address)
 * gets its own token bucket.
 *
 * Uses the Bucket4j library for efficient, thread-safe rate limiting
 * with Caffeine cache for automatic eviction to prevent memory leaks.
 * Buckets are evicted after 10 minutes of inactivity, and the cache
 * is limited to 10,000 entries.
 *
 * @property requestsPerMinute Maximum requests allowed per minute per key.
 *                             Defaults to 5 as specified in the user story.
 */
@Component
class RateLimiter(
    @Value("\${identity.rate-limiting.registration.requests-per-minute:5}")
    private val requestsPerMinute: Long
) {
    private val buckets: Cache<String, Bucket> = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build()

    /**
     * Attempts to acquire a permit for the given key.
     *
     * If the rate limit has not been exceeded, consumes one token
     * and returns `true`. Otherwise returns `false` without blocking.
     *
     * @param key The rate limit key (typically client IP address).
     * @return `true` if the request is allowed, `false` if rate limited.
     */
    fun tryAcquire(key: String): Boolean {
        val bucket = buckets.get(key) { createBucket() }
        return bucket.tryConsume(1)
    }

    /**
     * Creates a new token bucket with the configured rate limit.
     *
     * @return A new [Bucket] instance.
     */
    private fun createBucket(): Bucket {
        val limit = Bandwidth.simple(requestsPerMinute, Duration.ofMinutes(1))
        return Bucket.builder()
            .addLimit(limit)
            .build()
    }

    /**
     * Resets the rate limit for a specific key.
     *
     * This removes the bucket, so the next request from this key
     * will get a fresh bucket with full capacity.
     *
     * @param key The rate limit key to reset.
     */
    fun reset(key: String) {
        buckets.invalidate(key)
    }
}
