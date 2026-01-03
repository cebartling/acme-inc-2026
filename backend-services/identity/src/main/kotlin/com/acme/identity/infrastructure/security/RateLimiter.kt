package com.acme.identity.infrastructure.security

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Component
class RateLimiter(
    @Value("\${identity.rate-limiting.registration.requests-per-minute:5}")
    private val requestsPerMinute: Long
) {
    private val buckets = ConcurrentHashMap<String, Bucket>()

    fun tryAcquire(key: String): Boolean {
        val bucket = buckets.computeIfAbsent(key) { createBucket() }
        return bucket.tryConsume(1)
    }

    private fun createBucket(): Bucket {
        val limit = Bandwidth.simple(requestsPerMinute, Duration.ofMinutes(1))
        return Bucket.builder()
            .addLimit(limit)
            .build()
    }

    fun reset(key: String) {
        buckets.remove(key)
    }
}
