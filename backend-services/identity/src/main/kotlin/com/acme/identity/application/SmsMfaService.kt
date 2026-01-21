package com.acme.identity.application

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import arrow.core.right
import com.acme.identity.domain.MfaChallenge
import com.acme.identity.domain.MfaMethod
import com.acme.identity.domain.User
import com.acme.identity.domain.events.MFAChallengeInitiated
import com.acme.identity.domain.events.MFAVerificationFailed
import com.acme.identity.domain.events.MFAVerificationFailureReason
import com.acme.identity.domain.events.MFAVerificationSucceeded
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.EventStoreRepository
import com.acme.identity.infrastructure.persistence.MfaChallengeRepository
import com.acme.identity.infrastructure.persistence.UserRepository
import com.acme.identity.infrastructure.ratelimit.ResendCooldownResult
import com.acme.identity.infrastructure.ratelimit.SmsRateLimitResult
import com.acme.identity.infrastructure.ratelimit.SmsRateLimiter
import com.acme.identity.infrastructure.sms.SmsProvider
import com.acme.identity.infrastructure.sms.SmsResult
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

/**
 * Sealed interface representing SMS MFA errors.
 */
sealed interface SmsMfaError {
    /** User does not have SMS MFA configured. */
    data object SmsNotConfigured : SmsMfaError

    /** Phone number not verified. */
    data object PhoneNotVerified : SmsMfaError

    /** SMS rate limit exceeded. */
    data class RateLimited(val retryAfterSeconds: Long) : SmsMfaError

    /** Resend cooldown not elapsed. */
    data class CooldownActive(val waitSeconds: Long) : SmsMfaError

    /** Failed to send SMS. */
    data class SendFailed(val message: String) : SmsMfaError

    /** Invalid MFA token. */
    data object InvalidToken : SmsMfaError

    /** MFA challenge has expired. */
    data object Expired : SmsMfaError

    /** Invalid verification code. */
    data class InvalidCode(val remainingAttempts: Int) : SmsMfaError
}

/**
 * Result of creating an SMS MFA challenge.
 */
data class SmsChallengeResult(
    val mfaToken: String,
    val maskedPhone: String,
    val expiresIn: Long,
    val resendAvailableIn: Long
)

/**
 * Result of resending an SMS code.
 */
data class SmsResendResult(
    val maskedPhone: String,
    val expiresIn: Long,
    val resendAvailableIn: Long
)

/**
 * Result of successful SMS MFA verification.
 */
data class SmsMfaSuccess(
    val userId: UUID,
    val email: String,
    val firstName: String,
    val lastName: String,
    val deviceRemembered: Boolean
)

/**
 * Application service for SMS-based MFA.
 *
 * Handles:
 * - SMS code generation and sending
 * - Rate limiting (max 3 SMS per hour)
 * - Resend with cooldown (60 seconds)
 * - Code verification
 * - Event publishing
 */
@Service
class SmsMfaService(
    private val mfaChallengeRepository: MfaChallengeRepository,
    private val userRepository: UserRepository,
    private val eventStoreRepository: EventStoreRepository,
    private val userEventPublisher: UserEventPublisher,
    private val smsProvider: SmsProvider,
    private val smsRateLimiter: SmsRateLimiter,
    private val meterRegistry: MeterRegistry,

    @Value("\${identity.sms.code.length:6}")
    private val codeLength: Int,

    @Value("\${identity.sms.code.expiry-seconds:300}")
    private val codeExpirySeconds: Long
) {
    private val logger = LoggerFactory.getLogger(SmsMfaService::class.java)
    private val secureRandom = SecureRandom()

    /**
     * Creates an SMS MFA challenge for a user.
     *
     * Generates a random code, sends it via SMS, and creates a challenge record.
     *
     * @param user The user to create a challenge for.
     * @param correlationId The correlation ID for tracing.
     * @return Either an error or the challenge result.
     */
    @Transactional
    fun createChallenge(
        user: User,
        correlationId: UUID
    ): Either<SmsMfaError, SmsChallengeResult> = either {
        // Check SMS MFA is enabled
        ensure(user.smsMfaEnabled) {
            SmsMfaError.SmsNotConfigured
        }

        // Check phone is verified
        val phoneNumber = ensureNotNull(user.phoneNumber) {
            SmsMfaError.SmsNotConfigured
        }
        ensure(user.phoneVerified) {
            SmsMfaError.PhoneNotVerified
        }

        // Check rate limit
        when (val rateLimitResult = smsRateLimiter.checkRateLimit(user.id)) {
            is SmsRateLimitResult.Exceeded -> {
                incrementCounter("rate_limited")
                raise(SmsMfaError.RateLimited(rateLimitResult.retryAfterSeconds))
            }
            is SmsRateLimitResult.Allowed -> { /* OK */ }
        }

        // Delete any existing challenges for this user
        mfaChallengeRepository.deleteByUserId(user.id)

        // Generate code
        val code = generateCode()
        val codeHash = hashCode(code)

        // Create challenge
        val challenge = MfaChallenge.create(
            userId = user.id,
            method = MfaMethod.SMS,
            expirySeconds = codeExpirySeconds,
            codeHash = codeHash
        )
        mfaChallengeRepository.save(challenge)

        // Record rate limit BEFORE sending to prevent timing attacks
        // (if SMS send fails repeatedly, attackers can't bypass rate limiting)
        smsRateLimiter.recordSmsSent(user.id)

        // Send SMS
        val smsMessage = "Your ACME verification code is: $code. Valid for ${codeExpirySeconds / 60} minutes."
        smsProvider.send(phoneNumber, smsMessage).fold(
            ifLeft = { failure ->
                logger.error("Failed to send SMS to user {}: {}", user.id, failure.message)
                mfaChallengeRepository.delete(challenge)
                incrementCounter("send_failed")
                raise(SmsMfaError.SendFailed(failure.message))
            },
            ifRight = { success ->
                logger.info("SMS sent to user {} (message ID: {})", user.id, success.messageId)
            }
        )

        // Publish event
        val event = MFAChallengeInitiated.create(
            userId = user.id,
            mfaToken = challenge.token,
            method = MfaMethod.SMS.name,
            expiresAt = challenge.expiresAt,
            correlationId = correlationId
        )
        eventStoreRepository.append(event)
        userEventPublisher.publishMFAChallengeInitiated(event)

        incrementCounter("challenge_created")

        SmsChallengeResult(
            mfaToken = challenge.token,
            maskedPhone = maskPhoneNumber(phoneNumber),
            expiresIn = codeExpirySeconds,
            resendAvailableIn = smsRateLimiter.getResendAvailableIn(user.id)
        )
    }

    /**
     * Resends the SMS code for an existing challenge.
     *
     * @param mfaToken The MFA challenge token.
     * @param correlationId The correlation ID for tracing.
     * @return Either an error or the resend result.
     */
    @Transactional
    fun resendCode(
        mfaToken: String,
        correlationId: UUID
    ): Either<SmsMfaError, SmsResendResult> = either {
        // Find challenge
        val challenge = ensureNotNull(mfaChallengeRepository.findByToken(mfaToken)) {
            SmsMfaError.InvalidToken
        }

        // Check it's an SMS challenge
        ensure(challenge.method == MfaMethod.SMS) {
            SmsMfaError.InvalidToken
        }

        // Check not expired
        ensure(!challenge.isExpired()) {
            SmsMfaError.Expired
        }

        // Get user
        val user = ensureNotNull(userRepository.findById(challenge.userId).orElse(null)) {
            SmsMfaError.InvalidToken
        }

        val phoneNumber = ensureNotNull(user.phoneNumber) {
            SmsMfaError.SmsNotConfigured
        }

        // Check resend cooldown
        when (val cooldownResult = smsRateLimiter.checkResendCooldown(user.id)) {
            is ResendCooldownResult.Blocked -> {
                incrementCounter("cooldown_blocked")
                raise(SmsMfaError.CooldownActive(cooldownResult.waitSeconds))
            }
            is ResendCooldownResult.Allowed -> { /* OK */ }
        }

        // Check hourly rate limit
        when (val rateLimitResult = smsRateLimiter.checkRateLimit(user.id)) {
            is SmsRateLimitResult.Exceeded -> {
                incrementCounter("rate_limited")
                raise(SmsMfaError.RateLimited(rateLimitResult.retryAfterSeconds))
            }
            is SmsRateLimitResult.Allowed -> { /* OK */ }
        }

        // Generate new code
        val code = generateCode()
        val codeHash = hashCode(code)

        // Atomically update challenge with new code and extended expiry
        // This handles race conditions where the challenge may have been deleted
        val now = Instant.now()
        val newExpiresAt = now.plusSeconds(codeExpirySeconds)
        val updatedCount = mfaChallengeRepository.updateSmsChallenge(
            token = mfaToken,
            codeHash = codeHash,
            lastSentAt = now,
            expiresAt = newExpiresAt
        )

        // If no rows were updated, the challenge was deleted (race condition)
        ensure(updatedCount > 0) {
            logger.warn("Challenge {} was deleted during resend (race condition)", mfaToken.take(20))
            SmsMfaError.InvalidToken
        }

        // Record rate limit BEFORE sending to prevent timing attacks
        // (if SMS send fails repeatedly, attackers can't bypass rate limiting)
        smsRateLimiter.recordSmsSent(user.id)

        // Send SMS
        val smsMessage = "Your ACME verification code is: $code. Valid for ${codeExpirySeconds / 60} minutes."
        smsProvider.send(phoneNumber, smsMessage).fold(
            ifLeft = { failure ->
                logger.error("Failed to resend SMS to user {}: {}", user.id, failure.message)
                incrementCounter("resend_failed")
                raise(SmsMfaError.SendFailed(failure.message))
            },
            ifRight = { success ->
                logger.info("SMS resent to user {} (message ID: {})", user.id, success.messageId)
            }
        )
        incrementCounter("code_resent")

        SmsResendResult(
            maskedPhone = maskPhoneNumber(phoneNumber),
            expiresIn = codeExpirySeconds,
            resendAvailableIn = smsRateLimiter.getResendAvailableIn(user.id)
        )
    }

    /**
     * Verifies an SMS MFA code.
     *
     * @param mfaToken The MFA challenge token.
     * @param code The 6-digit code from the SMS.
     * @param rememberDevice Whether to remember this device.
     * @param context The verification context.
     * @return Either an error or success result.
     */
    @Transactional
    fun verifyCode(
        mfaToken: String,
        code: String,
        rememberDevice: Boolean,
        context: MfaVerificationContext
    ): Either<SmsMfaError, SmsMfaSuccess> = either {
        // Find challenge
        val challenge = ensureNotNull(mfaChallengeRepository.findByToken(mfaToken)) {
            incrementCounter("invalid_token")
            SmsMfaError.InvalidToken
        }

        // Check it's an SMS challenge
        ensure(challenge.method == MfaMethod.SMS) {
            incrementCounter("invalid_token")
            SmsMfaError.InvalidToken
        }

        // Check not expired
        ensure(!challenge.isExpired()) {
            logger.info("SMS MFA challenge expired for user {}", challenge.userId)
            publishFailedEvent(challenge, MFAVerificationFailureReason.CHALLENGE_EXPIRED, context)
            mfaChallengeRepository.delete(challenge)
            incrementCounter("expired")
            SmsMfaError.Expired
        }

        // Check max attempts
        ensure(!challenge.hasExceededMaxAttempts()) {
            logger.warn("SMS MFA max attempts exceeded for user {}", challenge.userId)
            publishFailedEvent(challenge, MFAVerificationFailureReason.MAX_ATTEMPTS_EXCEEDED, context)
            mfaChallengeRepository.delete(challenge)
            incrementCounter("max_attempts_exceeded")
            SmsMfaError.Expired
        }

        // Get user
        val user = ensureNotNull(userRepository.findById(challenge.userId).orElse(null)) {
            incrementCounter("user_not_found")
            SmsMfaError.InvalidToken
        }

        // Verify code hash
        val providedHash = hashCode(code)
        val isValid = providedHash == challenge.codeHash

        if (!isValid) {
            challenge.incrementAttempts()
            mfaChallengeRepository.save(challenge)

            val remainingAttempts = challenge.remainingAttempts()
            publishFailedEvent(challenge, MFAVerificationFailureReason.INVALID_CODE, context)
            incrementCounter("invalid_code")

            logger.info(
                "Invalid SMS code for user {} (attempt {}, {} remaining)",
                user.id, challenge.attempts, remainingAttempts
            )

            if (remainingAttempts == 0) {
                mfaChallengeRepository.delete(challenge)
                raise(SmsMfaError.Expired)
            }

            raise(SmsMfaError.InvalidCode(remainingAttempts))
        }

        // Success - delete challenge (single use)
        mfaChallengeRepository.delete(challenge)

        // Publish success event
        val event = MFAVerificationSucceeded.create(
            userId = user.id,
            method = MfaMethod.SMS.name,
            deviceRemembered = rememberDevice,
            correlationId = context.correlationId
        )
        eventStoreRepository.append(event)
        userEventPublisher.publishMFAVerificationSucceeded(event)

        incrementCounter("success")
        logger.info("SMS MFA verification successful for user {}", user.id)

        SmsMfaSuccess(
            userId = user.id,
            email = user.email,
            firstName = user.firstName,
            lastName = user.lastName,
            deviceRemembered = rememberDevice
        )
    }

    /**
     * Generates a cryptographically random numeric code.
     */
    private fun generateCode(): String {
        val code = StringBuilder()
        for (i in 0 until codeLength) {
            code.append(secureRandom.nextInt(10))
        }
        return code.toString()
    }

    /**
     * Hashes a code using SHA-256.
     */
    private fun hashCode(code: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(code.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Masks a phone number for display.
     * E.g., +15551234567 -> ***-***-4567
     */
    private fun maskPhoneNumber(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        // Handle edge case where phone has fewer than 4 digits
        if (digits.length < 4) return "***-***-****"
        val lastFour = digits.takeLast(4)
        return "***-***-$lastFour"
    }

    private fun publishFailedEvent(
        challenge: MfaChallenge,
        reason: MFAVerificationFailureReason,
        context: MfaVerificationContext
    ) {
        val event = MFAVerificationFailed.create(
            userId = challenge.userId,
            method = MfaMethod.SMS.name,
            reason = reason,
            attemptCount = challenge.attempts,
            correlationId = context.correlationId
        )
        eventStoreRepository.append(event)
        userEventPublisher.publishMFAVerificationFailed(event)
    }

    private fun incrementCounter(status: String) {
        meterRegistry.counter("sms_mfa_attempts_total", "status", status).increment()
    }
}
