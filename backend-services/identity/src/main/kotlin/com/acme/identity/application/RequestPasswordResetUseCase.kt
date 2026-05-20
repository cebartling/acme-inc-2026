package com.acme.identity.application

import com.acme.identity.domain.PasswordResetToken
import com.acme.identity.domain.events.PasswordResetRequested
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.EventStoreRepository
import com.acme.identity.infrastructure.persistence.PasswordResetTokenRepository
import com.acme.identity.infrastructure.persistence.UserRepository
import com.acme.identity.infrastructure.ratelimit.PasswordResetRateLimiter
import com.acme.identity.infrastructure.ratelimit.RateLimitResult
import com.acme.identity.infrastructure.security.PasswordResetTokenGenerator
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Outcome of a password-reset request. The controller always returns
 * 200 OK to the client (no email enumeration); this internal result
 * lets metrics distinguish the cases.
 */
sealed interface RequestPasswordResetResult {
    data object EmailSent : RequestPasswordResetResult
    data object UnknownEmail : RequestPasswordResetResult
    data object RateLimited : RequestPasswordResetResult
}

/**
 * Issues a single-use password-reset token to a customer and emits a
 * [PasswordResetRequested] event for the notification service to send
 * the reset email.
 *
 * Security properties:
 * - Response is identical whether or not the email maps to a real user.
 * - Rate limiting (3 / hour per email) is enforced before lookup; the
 *   request log records every attempt regardless of user existence so
 *   limits cannot be sidestepped by guessing emails.
 */
@Service
class RequestPasswordResetUseCase(
    private val userRepository: UserRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val eventStoreRepository: EventStoreRepository,
    private val userEventPublisher: UserEventPublisher,
    private val tokenGenerator: PasswordResetTokenGenerator,
    private val rateLimiter: PasswordResetRateLimiter,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun execute(
        email: String,
        ipAddress: String?,
        correlationId: UUID = UUID.randomUUID()
    ): RequestPasswordResetResult {
        val normalizedEmail = email.lowercase()

        when (val limit = rateLimiter.checkRateLimit(normalizedEmail)) {
            is RateLimitResult.Exceeded -> {
                logger.debug("Password reset rate limit exceeded for {}", normalizedEmail)
                incrementCounter("rate_limited")
                return RequestPasswordResetResult.RateLimited
            }
            is RateLimitResult.Allowed -> { /* fall through */ }
        }

        rateLimiter.record(normalizedEmail, ipAddress)

        val user = userRepository.findByEmail(normalizedEmail)
        if (user == null) {
            logger.debug("Password reset request for unknown email")
            incrementCounter("unknown_email")
            return RequestPasswordResetResult.UnknownEmail
        }

        val token = PasswordResetToken(
            id = UUID.randomUUID(),
            userId = user.id,
            token = tokenGenerator.generate(),
            expiresAt = tokenGenerator.calculateExpiration()
        )
        passwordResetTokenRepository.save(token)

        val event = PasswordResetRequested.create(
            userId = user.id,
            email = user.email,
            firstName = user.firstName,
            lastName = user.lastName,
            resetToken = token.token,
            expiresAt = token.expiresAt,
            ipAddress = ipAddress,
            correlationId = correlationId
        )
        eventStoreRepository.append(event)
        userEventPublisher.publishPasswordResetRequested(event)

        logger.info("Password reset email triggered for user: {}", user.id)
        incrementCounter("email_sent")
        return RequestPasswordResetResult.EmailSent
    }

    private fun incrementCounter(result: String) {
        meterRegistry.counter("password_reset_request_total", "result", result).increment()
    }
}
