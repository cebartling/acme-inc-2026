package com.acme.identity.application

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.acme.identity.domain.UserStatus
import com.acme.identity.domain.events.ActivationMethod
import com.acme.identity.domain.events.EmailVerified
import com.acme.identity.domain.events.UserActivated
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.EventStoreRepository
import com.acme.identity.infrastructure.persistence.UserRepository
import com.acme.identity.infrastructure.persistence.VerificationTokenRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Sealed interface representing possible email verification errors.
 *
 * Using Arrow's Either with a sealed error hierarchy provides type-safe
 * error handling with exhaustive pattern matching.
 */
sealed interface VerificationError {
    /**
     * Verification failed because the token has expired.
     */
    data object ExpiredToken : VerificationError

    /**
     * Verification failed because the token is invalid or doesn't exist.
     */
    data object InvalidToken : VerificationError

    /**
     * Verification failed because the email is already verified.
     */
    data object AlreadyVerified : VerificationError

    /**
     * Verification failed due to an unexpected error.
     *
     * @property message A human-readable error message.
     */
    data class InternalError(val message: String) : VerificationError
}

/**
 * Response data for successful email verification.
 *
 * @property userId The ID of the verified user.
 * @property email The verified email address.
 * @property activatedAt When the account was activated.
 */
data class VerificationSuccess(
    val userId: UUID,
    val email: String,
    val activatedAt: Instant
)

/**
 * Application service implementing the email verification use case.
 *
 * This service orchestrates the email verification flow including:
 * - Token validation (existence, expiration, usage)
 * - Marking email as verified
 * - Activating user account
 * - Publishing EmailVerified and UserActivated events
 * - Metrics collection
 *
 * The entire operation is transactional to ensure data consistency.
 *
 * @property userRepository Repository for user persistence.
 * @property verificationTokenRepository Repository for verification tokens.
 * @property eventStoreRepository Repository for event sourcing.
 * @property userEventPublisher Kafka event publisher.
 * @property meterRegistry Micrometer metrics registry.
 */
@Service
class VerifyEmailUseCase(
    private val userRepository: UserRepository,
    private val verificationTokenRepository: VerificationTokenRepository,
    private val eventStoreRepository: EventStoreRepository,
    private val userEventPublisher: UserEventPublisher,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(VerifyEmailUseCase::class.java)

    private val verificationTimer = Timer.builder("email_verification_duration_seconds")
        .description("Time taken to process email verification")
        .register(meterRegistry)

    /**
     * Executes the email verification use case.
     *
     * This method performs the complete verification flow within a database
     * transaction. Both EmailVerified and UserActivated events are persisted
     * atomically to ensure consistency.
     *
     * @param token The verification token from the email link.
     * @param correlationId Unique ID for distributed tracing.
     * @return [Either] containing either a [VerificationError] or [VerificationSuccess].
     */
    @Transactional
    fun execute(
        token: String,
        correlationId: UUID = UUID.randomUUID()
    ): Either<VerificationError, VerificationSuccess> {
        return verificationTimer.record<Either<VerificationError, VerificationSuccess>> {
            either {
                // Find the verification token
                val verificationToken = verificationTokenRepository.findByToken(token)

                ensureNotNull(verificationToken) {
                    logger.info("Verification attempt with non-existent token")
                    incrementVerificationCounter("invalid")
                    VerificationError.InvalidToken
                }

                // Check if token is already used
                ensure(!verificationToken.isUsed()) {
                    logger.info("Verification attempt with already-used token for user: {}", verificationToken.userId)
                    incrementVerificationCounter("already_verified")
                    VerificationError.AlreadyVerified
                }

                // Check if token is expired
                ensure(!verificationToken.isExpired()) {
                    logger.info("Verification attempt with expired token for user: {}", verificationToken.userId)
                    incrementVerificationCounter("expired")
                    VerificationError.ExpiredToken
                }

                // Find the user
                val user = userRepository.findById(verificationToken.userId).orElse(null)
                ensureNotNull(user) {
                    logger.error("User not found for valid verification token: {}", verificationToken.userId)
                    incrementVerificationCounter("error")
                    VerificationError.InternalError("Verification failed due to an internal error.")
                }

                // Check if user is already verified
                ensure(!user.emailVerified) {
                    logger.info("Verification attempt for already-verified user: {}", user.id)
                    // Mark token as used anyway to prevent reuse
                    verificationToken.markAsUsed()
                    verificationTokenRepository.save(verificationToken)
                    incrementVerificationCounter("already_verified")
                    VerificationError.AlreadyVerified
                }

                val now = Instant.now()

                // Mark token as used
                verificationToken.markAsUsed()
                verificationTokenRepository.save(verificationToken)

                // Activate user and mark email as verified
                val activatedUser = user.activate()
                activatedUser.markEmailAsVerified()
                userRepository.save(activatedUser)

                // Create domain events
                val emailVerifiedEvent = EmailVerified.create(
                    userId = activatedUser.id,
                    email = activatedUser.email,
                    verifiedAt = now,
                    correlationId = correlationId
                )

                val userActivatedEvent = UserActivated.create(
                    userId = activatedUser.id,
                    activatedAt = now,
                    activationMethod = ActivationMethod.EMAIL_VERIFICATION,
                    correlationId = correlationId
                )

                // Persist events to event store atomically
                eventStoreRepository.append(emailVerifiedEvent)
                eventStoreRepository.append(userActivatedEvent)

                // Publish events to Kafka (async)
                userEventPublisher.publishEmailVerified(emailVerifiedEvent)
                userEventPublisher.publishUserActivated(userActivatedEvent)

                logger.info(
                    "Email verified and account activated for user: {} with email: {}",
                    activatedUser.id,
                    activatedUser.email
                )
                incrementVerificationCounter("success")

                VerificationSuccess(
                    userId = activatedUser.id,
                    email = activatedUser.email,
                    activatedAt = now
                )
            }
        } ?: Either.Left(VerificationError.InternalError("Verification failed: unexpected null result"))
    }

    /**
     * Increments the email verification counter with the given result status.
     *
     * @param result The outcome result (success, expired, invalid, already_verified, error).
     */
    private fun incrementVerificationCounter(result: String) {
        meterRegistry.counter("email_verification_total", "result", result).increment()
    }
}
