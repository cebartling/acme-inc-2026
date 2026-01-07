package com.acme.identity.application

import com.acme.identity.domain.VerificationResendRequest
import com.acme.identity.domain.VerificationToken
import com.acme.identity.domain.events.UserRegistered
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.EventStoreRepository
import com.acme.identity.infrastructure.persistence.ResendRequestRepository
import com.acme.identity.infrastructure.persistence.UserRepository
import com.acme.identity.infrastructure.persistence.VerificationTokenRepository
import com.acme.identity.infrastructure.ratelimit.RateLimitResult
import com.acme.identity.infrastructure.ratelimit.VerificationRateLimiter
import com.acme.identity.infrastructure.security.VerificationTokenGenerator
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Sealed class representing the possible outcomes of resending verification email.
 *
 * Using a sealed class allows exhaustive when-expressions and provides
 * type-safe error handling without exceptions for expected failure cases.
 */
sealed class ResendVerificationResult {
    /**
     * Resend request processed successfully.
     *
     * Note: For security reasons, this is returned even if the email doesn't exist.
     *
     * @property message A human-readable success message.
     * @property requestsRemaining Number of resend requests remaining.
     */
    data class Success(
        val message: String,
        val requestsRemaining: Int
    ) : ResendVerificationResult()

    /**
     * Resend failed because the rate limit was exceeded.
     *
     * @property message A human-readable error message.
     * @property retryAfter When the rate limit will reset.
     */
    data class RateLimited(
        val message: String,
        val retryAfter: Instant
    ) : ResendVerificationResult()

    /**
     * Resend failed due to an unexpected error.
     *
     * @property message A human-readable error message.
     */
    data class Error(val message: String) : ResendVerificationResult()
}

/**
 * Application service implementing the resend verification email use case.
 *
 * This service orchestrates the resend flow including:
 * - Rate limiting (3 requests per hour)
 * - Creating new verification token
 * - Publishing UserRegistered event (to trigger notification service)
 * - Metrics collection
 *
 * For security reasons, the response is the same whether the email exists or not.
 *
 * @property userRepository Repository for user persistence.
 * @property verificationTokenRepository Repository for verification tokens.
 * @property resendRequestRepository Repository for rate limit tracking.
 * @property eventStoreRepository Repository for event sourcing.
 * @property userEventPublisher Kafka event publisher.
 * @property verificationTokenGenerator Token generator.
 * @property verificationRateLimiter Rate limiter service.
 * @property meterRegistry Micrometer metrics registry.
 */
@Service
class ResendVerificationUseCase(
    private val userRepository: UserRepository,
    private val verificationTokenRepository: VerificationTokenRepository,
    private val resendRequestRepository: ResendRequestRepository,
    private val eventStoreRepository: EventStoreRepository,
    private val userEventPublisher: UserEventPublisher,
    private val verificationTokenGenerator: VerificationTokenGenerator,
    private val verificationRateLimiter: VerificationRateLimiter,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(ResendVerificationUseCase::class.java)

    private val resendTimer = Timer.builder("resend_verification_duration_seconds")
        .description("Time taken to process resend verification request")
        .register(meterRegistry)

    /**
     * Executes the resend verification email use case.
     *
     * This method performs the resend flow within a database transaction.
     * For security reasons, the response is the same whether the email
     * exists or not, to prevent email enumeration attacks.
     *
     * @param email The email address to resend verification to.
     * @param ipAddress The IP address of the requester (for rate limiting).
     * @param correlationId Unique ID for distributed tracing.
     * @return [ResendVerificationResult] indicating success or failure.
     */
    @Transactional
    fun execute(
        email: String,
        ipAddress: String? = null,
        correlationId: UUID = UUID.randomUUID()
    ): ResendVerificationResult {
        return resendTimer.record<ResendVerificationResult> {
            try {
                executeInternal(email, ipAddress, correlationId)
            } catch (e: Exception) {
                logger.error("Resend verification failed for email: {}", email, e)
                incrementResendCounter("error")
                ResendVerificationResult.Error("Failed to process request. Please try again later.")
            }
        } ?: ResendVerificationResult.Error("Resend failed: unexpected null result")
    }

    /**
     * Internal implementation of the resend logic.
     *
     * @param email The email address.
     * @param ipAddress The IP address of the requester.
     * @param correlationId The correlation ID for tracing.
     * @return The resend result.
     */
    private fun executeInternal(
        email: String,
        ipAddress: String?,
        correlationId: UUID
    ): ResendVerificationResult {
        val normalizedEmail = email.lowercase()

        // Check rate limit first
        when (val rateLimitResult = verificationRateLimiter.checkRateLimit(normalizedEmail)) {
            is RateLimitResult.Exceeded -> {
                logger.info("Rate limit exceeded for resend verification: {}", normalizedEmail)
                incrementResendCounter("rate_limited")
                meterRegistry.counter("rate_limit_exceeded_total", "type", "resend_verification").increment()
                return ResendVerificationResult.RateLimited(
                    message = "Too many requests. Please try again later.",
                    retryAfter = rateLimitResult.retryAfter
                )
            }
            is RateLimitResult.Allowed -> {
                // Continue processing
            }
        }

        // Record the resend request for rate limiting
        val resendRequest = VerificationResendRequest(
            id = UUID.randomUUID(),
            email = normalizedEmail,
            ipAddress = ipAddress
        )
        resendRequestRepository.save(resendRequest)

        // Get remaining requests after recording this one
        val (remaining, _) = verificationRateLimiter.getRateLimitInfo(normalizedEmail)

        // Find user - but don't reveal if user doesn't exist
        val user = userRepository.findByEmail(normalizedEmail)

        if (user == null) {
            logger.debug("Resend request for non-existent email: {}", normalizedEmail)
            // Return success to prevent email enumeration
            incrementResendCounter("success")
            return ResendVerificationResult.Success(
                message = "If an account exists with this email, a verification link has been sent.",
                requestsRemaining = remaining
            )
        }

        // Check if user is already verified
        if (user.emailVerified || user.isActive()) {
            logger.debug("Resend request for already-verified user: {}", user.id)
            // Return success to prevent information leakage
            incrementResendCounter("success")
            return ResendVerificationResult.Success(
                message = "If an account exists with this email, a verification link has been sent.",
                requestsRemaining = remaining
            )
        }

        // Generate new verification token
        val verificationToken = VerificationToken(
            id = UUID.randomUUID(),
            userId = user.id,
            token = verificationTokenGenerator.generate(),
            expiresAt = verificationTokenGenerator.calculateExpiration()
        )
        verificationTokenRepository.save(verificationToken)

        // Create UserRegistered event (notification service listens for this)
        val event = UserRegistered.create(
            userId = user.id,
            email = user.email,
            firstName = user.firstName,
            lastName = user.lastName,
            tosAcceptedAt = user.tosAcceptedAt,
            marketingOptIn = user.marketingOptIn,
            registrationSource = user.registrationSource,
            verificationToken = verificationToken.token,
            correlationId = correlationId
        )

        // Persist to event store
        eventStoreRepository.append(event)

        // Publish event to trigger notification
        userEventPublisher.publish(event)

        logger.info("Resend verification email triggered for user: {}", user.id)
        incrementResendCounter("success")

        return ResendVerificationResult.Success(
            message = "If an account exists with this email, a verification link has been sent.",
            requestsRemaining = remaining
        )
    }

    /**
     * Increments the resend verification counter with the given result status.
     *
     * @param result The outcome result (success, rate_limited, error).
     */
    private fun incrementResendCounter(result: String) {
        meterRegistry.counter("resend_verification_total", "result", result).increment()
    }
}
