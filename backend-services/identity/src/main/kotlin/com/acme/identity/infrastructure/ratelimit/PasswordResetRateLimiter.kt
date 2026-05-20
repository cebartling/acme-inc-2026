package com.acme.identity.infrastructure.ratelimit

import com.acme.identity.domain.PasswordResetRequestLog
import com.acme.identity.infrastructure.persistence.PasswordResetRequestLogRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Rate limiter for password reset requests.
 *
 * Enforces a per-email limit (default 3 / hour, per US-0003-13) using
 * persistent storage so limits survive restarts and span replicas.
 * Mirrors [VerificationRateLimiter]'s design and reuses [RateLimitResult].
 *
 * Recording happens explicitly via [record] — `checkRateLimit` is a
 * pure query so it can be called before deciding whether to do work
 * (e.g. timing-attack-safe paths still increment to keep limits honest).
 */
@Service
class PasswordResetRateLimiter(
    private val logRepository: PasswordResetRequestLogRepository,
    @Value("\${identity.rate-limiting.enabled:true}")
    private val enabled: Boolean = true,
    @Value("\${identity.rate-limiting.password-reset.requests-per-hour:3}")
    private val maxRequestsPerHour: Int = 3
) {
    fun checkRateLimit(email: String): RateLimitResult {
        if (!enabled) {
            return RateLimitResult.Allowed(remaining = maxRequestsPerHour)
        }

        val normalizedEmail = email.lowercase()
        val oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS)
        val recent = logRepository.countByEmailSince(normalizedEmail, oneHourAgo)

        return if (recent >= maxRequestsPerHour) {
            val oldest = logRepository.findOldestByEmailSince(normalizedEmail, oneHourAgo)
            val retryAfter = oldest?.requestedAt?.plus(1, ChronoUnit.HOURS)
                ?: Instant.now().plus(1, ChronoUnit.HOURS)
            RateLimitResult.Exceeded(retryAfter = retryAfter)
        } else {
            RateLimitResult.Allowed(remaining = (maxRequestsPerHour - recent).toInt())
        }
    }

    fun record(email: String, ipAddress: String?) {
        logRepository.save(
            PasswordResetRequestLog(
                id = UUID.randomUUID(),
                email = email.lowercase(),
                ipAddress = ipAddress,
                requestedAt = Instant.now()
            )
        )
    }
}
