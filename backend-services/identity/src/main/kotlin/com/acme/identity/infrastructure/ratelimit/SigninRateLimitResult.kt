package com.acme.identity.infrastructure.ratelimit

import java.time.Instant

/**
 * Result of a signin rate-limit check.
 *
 * Distinct from [RateLimitResult] (used by the verification/password-reset
 * limiters) because signin rate limiting must surface the per-scope limit,
 * remaining count, and reset timestamp via response headers in addition to
 * a retry-after value when limited.
 */
sealed interface SigninRateLimitResult {
    val limit: Int
    val remaining: Int
    val resetAt: Instant

    data class Allowed(
        override val limit: Int,
        override val remaining: Int,
        override val resetAt: Instant
    ) : SigninRateLimitResult

    data class Limited(
        override val limit: Int,
        override val resetAt: Instant,
        val retryAfterSeconds: Int,
        val scope: Scope,
        val currentCount: Int
    ) : SigninRateLimitResult {
        override val remaining: Int = 0
    }

    enum class Scope { IP, EMAIL }
}
