package com.acme.identity.application

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.acme.identity.domain.MfaChallenge
import com.acme.identity.domain.MfaMethod
import com.acme.identity.domain.UsedTotpCode
import com.acme.identity.domain.events.MFAVerificationFailed
import com.acme.identity.domain.events.MFAVerificationFailureReason
import com.acme.identity.domain.events.MFAVerificationSucceeded
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.EventStoreRepository
import com.acme.identity.infrastructure.persistence.MfaChallengeRepository
import com.acme.identity.infrastructure.persistence.UsedTotpCodeRepository
import com.acme.identity.infrastructure.persistence.UserRepository
import com.acme.identity.infrastructure.security.TotpService
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Sealed interface representing possible MFA verification errors.
 */
sealed interface MfaVerificationError {
    /**
     * The MFA token is invalid or not found.
     */
    data object InvalidToken : MfaVerificationError

    /**
     * The MFA challenge has expired (either by time or max attempts).
     */
    data object Expired : MfaVerificationError

    /**
     * The verification code was invalid.
     *
     * @property remainingAttempts The number of attempts remaining.
     */
    data class InvalidCode(val remainingAttempts: Int) : MfaVerificationError

    /**
     * The code has already been used within its validity window.
     *
     * @property remainingAttempts The number of attempts remaining.
     */
    data class CodeAlreadyUsed(val remainingAttempts: Int) : MfaVerificationError
}

/**
 * Result of successful MFA verification.
 *
 * @property userId The authenticated user's ID.
 * @property deviceRemembered Whether the user opted to remember the device.
 */
data class MfaVerificationResult(
    val userId: UUID,
    val deviceRemembered: Boolean
)

/**
 * Request DTO for MFA verification.
 *
 * @property mfaToken The MFA challenge token from signin.
 * @property code The 6-digit verification code.
 * @property method The MFA method (TOTP, SMS, EMAIL).
 * @property rememberDevice Whether to remember this device.
 */
data class MfaVerificationRequest(
    val mfaToken: String,
    val code: String,
    val method: MfaMethod,
    val rememberDevice: Boolean = false
)

/**
 * Context for MFA verification containing request metadata.
 */
data class MfaVerificationContext(
    val ipAddress: String,
    val userAgent: String,
    val correlationId: UUID = UUID.randomUUID()
)

/**
 * Application service implementing the MFA verification use case.
 *
 * This service handles TOTP code verification including:
 * - Challenge lookup and validation
 * - TOTP code verification with time tolerance
 * - Single-use code enforcement (replay prevention)
 * - Attempt tracking and lockout
 * - Event store persistence
 * - Kafka event publishing
 * - Metrics collection
 *
 * @property mfaChallengeRepository Repository for MFA challenges.
 * @property usedTotpCodeRepository Repository for used TOTP codes.
 * @property userRepository Repository for user persistence.
 * @property eventStoreRepository Repository for event sourcing.
 * @property userEventPublisher Kafka event publisher.
 * @property totpService TOTP code verification service.
 * @property meterRegistry Micrometer metrics registry.
 */
@Service
class VerifyMfaUseCase(
    private val mfaChallengeRepository: MfaChallengeRepository,
    private val usedTotpCodeRepository: UsedTotpCodeRepository,
    private val userRepository: UserRepository,
    private val eventStoreRepository: EventStoreRepository,
    private val userEventPublisher: UserEventPublisher,
    private val totpService: TotpService,
    private val meterRegistry: MeterRegistry,
    private val mfaChallengeService: MfaChallengeService
) {
    private val logger = LoggerFactory.getLogger(VerifyMfaUseCase::class.java)

    private val mfaVerificationTimer = Timer.builder("mfa_verification_duration_seconds")
        .description("Time taken to process MFA verification")
        .register(meterRegistry)

    /**
     * Executes the MFA verification use case.
     *
     * This method verifies a TOTP code against an active MFA challenge.
     * On success, the challenge is deleted and the user is fully authenticated.
     * On failure, the attempt count is incremented and appropriate errors are returned.
     *
     * @param request The MFA verification request.
     * @param context The verification context with metadata.
     * @return [Either] containing either an [MfaVerificationError] or [MfaVerificationResult].
     */
    @Transactional
    fun execute(
        request: MfaVerificationRequest,
        context: MfaVerificationContext
    ): Either<MfaVerificationError, MfaVerificationResult> {
        return mfaVerificationTimer.record<Either<MfaVerificationError, MfaVerificationResult>> {
            either {
                // Find the challenge by token
                val challenge = ensureNotNull(mfaChallengeRepository.findByToken(request.mfaToken)) {
                    logger.warn("MFA challenge not found for token: {}", request.mfaToken.take(20))
                    incrementMfaCounter("invalid_token")
                    MfaVerificationError.InvalidToken
                }

                // Check if challenge has expired
                ensure(!challenge.isExpired()) {
                    logger.info("MFA challenge expired for user {}", challenge.userId)
                    publishFailedEvent(challenge, MFAVerificationFailureReason.CHALLENGE_EXPIRED, context)
                    mfaChallengeRepository.delete(challenge)
                    incrementMfaCounter("expired")
                    MfaVerificationError.Expired
                }

                // Check if max attempts exceeded
                ensure(!challenge.hasExceededMaxAttempts()) {
                    logger.warn("MFA max attempts exceeded for user {}", challenge.userId)
                    publishFailedEvent(challenge, MFAVerificationFailureReason.MAX_ATTEMPTS_EXCEEDED, context)
                    mfaChallengeRepository.delete(challenge)
                    incrementMfaCounter("max_attempts_exceeded")
                    MfaVerificationError.Expired
                }

                // Get the user
                val user = ensureNotNull(userRepository.findById(challenge.userId).orElse(null)) {
                    logger.error("User not found for MFA challenge: {}", challenge.userId)
                    incrementMfaCounter("user_not_found")
                    MfaVerificationError.InvalidToken
                }

                // Get the user's TOTP secret
                val totpSecret = ensureNotNull(user.totpSecret) {
                    logger.error("TOTP secret not configured for user: {}", user.id)
                    incrementMfaCounter("totp_not_configured")
                    MfaVerificationError.InvalidToken
                }

                // Check if code was already used (replay prevention)
                val currentTimeStep = totpService.getCurrentTimeStep()
                val codeHash = totpService.hashCode(request.code)

                // Check all time steps in tolerance window with a single query (avoids N+1)
                val timeStepsToCheck = (-TotpService.TIME_TOLERANCE_STEPS..TotpService.TIME_TOLERANCE_STEPS)
                    .map { offset -> currentTimeStep + offset }

                if (usedTotpCodeRepository.existsByUserIdAndCodeHashAndTimeStepIn(user.id, codeHash, timeStepsToCheck)) {
                    challenge.incrementAttempts()
                    mfaChallengeRepository.save(challenge)
                    publishFailedEvent(challenge, MFAVerificationFailureReason.CODE_ALREADY_USED, context)
                    incrementMfaCounter("code_already_used")
                    raise(MfaVerificationError.CodeAlreadyUsed(remainingAttempts = challenge.remainingAttempts()))
                }

                // Verify the TOTP code
                val isValid = totpService.verifyCode(totpSecret, request.code)

                if (!isValid) {
                    challenge.incrementAttempts()
                    mfaChallengeRepository.save(challenge)

                    val remainingAttempts = challenge.remainingAttempts()
                    publishFailedEvent(challenge, MFAVerificationFailureReason.INVALID_CODE, context)
                    incrementMfaCounter("invalid_code")

                    logger.info(
                        "Invalid TOTP code for user {} (attempt {}, {} remaining)",
                        user.id,
                        challenge.attempts,
                        remainingAttempts
                    )

                    // Check if this was the last attempt
                    if (remainingAttempts == 0) {
                        mfaChallengeRepository.delete(challenge)
                        raise(MfaVerificationError.Expired)
                    }

                    raise(MfaVerificationError.InvalidCode(remainingAttempts = remainingAttempts))
                }

                // Code is valid - mark it as used
                val usedCode = UsedTotpCode.create(
                    userId = user.id,
                    codeHash = codeHash,
                    timeStep = currentTimeStep
                )
                usedTotpCodeRepository.save(usedCode)

                // Delete the challenge
                mfaChallengeRepository.delete(challenge)

                // Publish success event
                publishSuccessEvent(challenge, request.rememberDevice, context)
                incrementMfaCounter("success")

                logger.info("MFA verification successful for user {}", user.id)

                MfaVerificationResult(
                    userId = user.id,
                    deviceRemembered = request.rememberDevice
                )
            }
        } ?: Either.Left(MfaVerificationError.InvalidToken)
    }

    /**
     * Creates an MFA challenge for a user after successful credential validation.
     *
     * Delegates to MfaChallengeService for the actual implementation.
     *
     * @param userId The user's ID.
     * @param method The MFA method to use.
     * @param correlationId The correlation ID for tracing.
     * @return The created MFA challenge.
     */
    fun createChallenge(userId: UUID, method: MfaMethod, correlationId: UUID): MfaChallenge {
        return mfaChallengeService.createChallenge(userId, method, correlationId)
    }

    /**
     * Publishes an MFA verification failed event.
     */
    private fun publishFailedEvent(
        challenge: MfaChallenge,
        reason: MFAVerificationFailureReason,
        context: MfaVerificationContext
    ) {
        val event = MFAVerificationFailed.create(
            userId = challenge.userId,
            method = challenge.method.name,
            reason = reason,
            attemptCount = challenge.attempts,
            correlationId = context.correlationId
        )
        eventStoreRepository.append(event)
        userEventPublisher.publishMFAVerificationFailed(event)
    }

    /**
     * Publishes an MFA verification succeeded event.
     */
    private fun publishSuccessEvent(
        challenge: MfaChallenge,
        deviceRemembered: Boolean,
        context: MfaVerificationContext
    ) {
        val event = MFAVerificationSucceeded.create(
            userId = challenge.userId,
            method = challenge.method.name,
            deviceRemembered = deviceRemembered,
            correlationId = context.correlationId
        )
        eventStoreRepository.append(event)
        userEventPublisher.publishMFAVerificationSucceeded(event)
    }

    /**
     * Increments the MFA verification counter with the given status.
     */
    private fun incrementMfaCounter(status: String) {
        meterRegistry.counter("mfa_verification_attempts_total", "status", status).increment()
    }
}
