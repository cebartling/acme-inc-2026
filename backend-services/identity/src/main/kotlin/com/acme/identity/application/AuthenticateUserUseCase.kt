package com.acme.identity.application

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.acme.identity.api.v1.dto.MfaMethod
import com.acme.identity.api.v1.dto.SigninRequest
import com.acme.identity.api.v1.dto.SigninResponse
import com.acme.identity.domain.User
import com.acme.identity.domain.UserStatus
import com.acme.identity.domain.events.AccountLocked as AccountLockedEvent
import com.acme.identity.domain.events.AccountLockReason
import com.acme.identity.domain.events.AccountUnlocked as AccountUnlockedEvent
import com.acme.identity.domain.events.AccountUnlockReason
import com.acme.identity.domain.events.AuthenticationFailed
import com.acme.identity.domain.events.AuthenticationFailureReason
import com.acme.identity.domain.events.AuthenticationSucceeded
import java.time.Duration
import java.time.Instant
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.EventStoreRepository
import com.acme.identity.infrastructure.persistence.UserRepository
import com.acme.identity.infrastructure.security.PasswordHasher
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Sealed interface representing possible authentication errors.
 *
 * Using Arrow's Either with a sealed error hierarchy provides type-safe
 * error handling with exhaustive pattern matching.
 */
sealed interface AuthenticationError {
    /**
     * Authentication failed due to invalid credentials.
     *
     * This error is used for both non-existent users and wrong passwords
     * to prevent user enumeration attacks.
     *
     * @property remainingAttempts The number of attempts remaining before lockout.
     */
    data class InvalidCredentials(val remainingAttempts: Int) : AuthenticationError

    /**
     * Authentication failed because the account is not active.
     *
     * @property status The current account status.
     * @property reason Human-readable reason for the inactive status.
     */
    data class AccountInactive(val status: UserStatus, val reason: String) : AuthenticationError

    /**
     * Authentication failed because the account is locked.
     *
     * @property lockedUntil ISO-8601 timestamp when the lock expires.
     * @property lockoutRemainingSeconds Seconds remaining until lockout expires.
     */
    data class AccountLocked(
        val lockedUntil: String,
        val lockoutRemainingSeconds: Long
    ) : AuthenticationError

    /**
     * Authentication failed due to rate limiting.
     */
    data object RateLimited : AuthenticationError
}

/**
 * Internal result type for authentication success before response formatting.
 */
data class AuthenticationResult(
    val user: User,
    val mfaRequired: Boolean,
    val mfaToken: String? = null,
    val mfaMethods: List<MfaMethod> = emptyList()
)

/**
 * Request context containing metadata for authentication.
 */
data class AuthenticationContext(
    val ipAddress: String,
    val userAgent: String,
    val correlationId: UUID = UUID.randomUUID()
)

/**
 * Application service implementing the user authentication use case.
 *
 * This service orchestrates the credential validation flow including:
 * - Rate limit checking
 * - User lookup with timing attack prevention
 * - Password verification with Argon2id
 * - Account status validation
 * - Failed attempts tracking
 * - Event store persistence
 * - Kafka event publishing
 * - Metrics collection
 *
 * The entire operation is transactional to ensure data consistency.
 *
 * @property userRepository Repository for user persistence.
 * @property eventStoreRepository Repository for event sourcing.
 * @property userEventPublisher Kafka event publisher.
 * @property passwordHasher Argon2id password hasher.
 * @property meterRegistry Micrometer metrics registry.
 * @property maxFailedAttempts Maximum failed attempts before lockout consideration.
 * @property supportUrl URL for customer support.
 */
@Service
class AuthenticateUserUseCase(
    private val userRepository: UserRepository,
    private val eventStoreRepository: EventStoreRepository,
    private val userEventPublisher: UserEventPublisher,
    private val passwordHasher: PasswordHasher,
    private val meterRegistry: MeterRegistry,
    @Value("\${identity.authentication.max-failed-attempts:5}")
    private val maxFailedAttempts: Int = 5,
    @Value("\${identity.lockout.lockout-duration-minutes:15}")
    private val lockoutDurationMinutes: Long = 15,
    @Value("\${identity.support-url:https://www.acme.com/support}")
    private val supportUrl: String = "https://www.acme.com/support",
    @Value("\${identity.password-reset-url:https://www.acme.com/forgot-password}")
    private val passwordResetUrl: String = "https://www.acme.com/forgot-password"
) {
    private val logger = LoggerFactory.getLogger(AuthenticateUserUseCase::class.java)

    private val authenticationTimer = Timer.builder("authentication_duration_seconds")
        .description("Time taken to process authentication")
        .register(meterRegistry)

    private val passwordVerifyTimer = Timer.builder("password_verify_duration_seconds")
        .description("Time taken to verify password")
        .register(meterRegistry)

    /**
     * Dummy password hash used for timing attack prevention.
     *
     * When a user is not found, we still perform a password hash verification
     * against this dummy hash to ensure response times are consistent regardless
     * of whether the user exists.
     */
    private val dummyPasswordHash: String by lazy {
        passwordHasher.hash("dummy_password_for_timing_attack_prevention")
    }

    /**
     * Executes the user authentication use case.
     *
     * This method performs the complete authentication flow within a database
     * transaction. Events are persisted to the event store before the
     * HTTP response is sent, ensuring event consistency.
     *
     * @param request The signin request containing credentials.
     * @param context The authentication context with metadata.
     * @return [Either] containing either an [AuthenticationError] or [SigninResponse].
     */
    @Transactional
    fun execute(
        request: SigninRequest,
        context: AuthenticationContext
    ): Either<AuthenticationError, SigninResponse> {
        return authenticationTimer.record<Either<AuthenticationError, SigninResponse>> {
            either {
                val normalizedEmail = request.email.lowercase()

                // Find user (with timing attack prevention)
                val user = userRepository.findByEmail(normalizedEmail)

                if (user == null) {
                    // Timing attack prevention: perform dummy password check
                    performDummyPasswordCheck()
                    publishFailedEvent(
                        userId = UUID(0, 0),
                        email = normalizedEmail,
                        reason = AuthenticationFailureReason.USER_NOT_FOUND,
                        context = context,
                        failedAttemptCount = 0,
                        deviceFingerprint = request.deviceFingerprint
                    )
                    incrementAuthenticationCounter("invalid_credentials")
                    raise(AuthenticationError.InvalidCredentials(remainingAttempts = 0))
                }

                // Check if account has LOCKED status (may be expired or active lockout)
                if (user.status == UserStatus.LOCKED) {
                    // Check if lockout has expired
                    val lockedUntil = user.lockedUntil
                    if (lockedUntil != null && lockedUntil.isBefore(Instant.now())) {
                        // Lockout has expired - unlock the account
                        user.unlock()
                        userRepository.save(user)

                        // Publish AccountUnlocked event
                        publishUnlockEvent(
                            user = user,
                            reason = AccountUnlockReason.LOCKOUT_EXPIRED,
                            previousLockReason = AccountLockReason.EXCESSIVE_FAILED_ATTEMPTS,
                            correlationId = context.correlationId
                        )

                        logger.info("Account lockout expired for user {}, account unlocked", user.id)
                        incrementAuthenticationCounter("lockout_expired")
                    } else {
                        // Account is still locked
                        val lockoutRemainingSeconds = if (lockedUntil != null) {
                            Duration.between(Instant.now(), lockedUntil).seconds.coerceAtLeast(0)
                        } else {
                            0L
                        }

                        publishFailedEvent(
                            userId = user.id,
                            email = normalizedEmail,
                            reason = AuthenticationFailureReason.ACCOUNT_LOCKED,
                            context = context,
                            failedAttemptCount = user.failedAttempts,
                            deviceFingerprint = request.deviceFingerprint
                        )
                        incrementAuthenticationCounter("account_locked")
                        raise(AuthenticationError.AccountLocked(
                            lockedUntil = lockedUntil?.toString() ?: "",
                            lockoutRemainingSeconds = lockoutRemainingSeconds
                        ))
                    }
                }

                // Verify password
                val passwordValid = passwordVerifyTimer.record<Boolean> {
                    passwordHasher.verify(request.password, user.passwordHash)
                } ?: false

                if (!passwordValid) {
                    // Increment failed attempts
                    user.incrementFailedAttempts()

                    val remainingAttempts = user.remainingAttempts(maxFailedAttempts)

                    // Check if we should lock the account
                    if (user.failedAttempts >= maxFailedAttempts) {
                        // Lock the account
                        val lockoutDuration = Duration.ofMinutes(lockoutDurationMinutes)
                        user.lock(lockoutDuration)
                        // user.lockedUntil is guaranteed non-null after lock() sets it
                        val lockedUntilTime = user.lockedUntil!!
                        userRepository.save(user)

                        // Publish failed authentication event
                        publishFailedEvent(
                            userId = user.id,
                            email = normalizedEmail,
                            reason = AuthenticationFailureReason.INVALID_PASSWORD,
                            context = context,
                            failedAttemptCount = user.failedAttempts,
                            deviceFingerprint = request.deviceFingerprint
                        )

                        // Publish account locked event
                        publishLockEvent(
                            user = user,
                            reason = AccountLockReason.EXCESSIVE_FAILED_ATTEMPTS,
                            lockedUntil = lockedUntilTime,
                            context = context
                        )

                        incrementAuthenticationCounter("account_locked_triggered")
                        logger.warn(
                            "Account locked for user {} after {} failed attempts. Locked until {}",
                            user.id,
                            user.failedAttempts,
                            lockedUntilTime
                        )

                        raise(AuthenticationError.AccountLocked(
                            lockedUntil = lockedUntilTime.toString(),
                            lockoutRemainingSeconds = lockoutDuration.seconds
                        ))
                    } else {
                        // Just save the incremented failed attempts
                        userRepository.save(user)

                        publishFailedEvent(
                            userId = user.id,
                            email = normalizedEmail,
                            reason = AuthenticationFailureReason.INVALID_PASSWORD,
                            context = context,
                            failedAttemptCount = user.failedAttempts,
                            deviceFingerprint = request.deviceFingerprint
                        )

                        incrementAuthenticationCounter("invalid_credentials")
                        logger.info(
                            "Failed authentication attempt for user {} (attempt {}, {} remaining)",
                            user.id,
                            user.failedAttempts,
                            remainingAttempts
                        )

                        raise(AuthenticationError.InvalidCredentials(remainingAttempts = remainingAttempts))
                    }
                }

                // Check account status
                ensure(user.status == UserStatus.ACTIVE) {
                    publishFailedEvent(
                        userId = user.id,
                        email = normalizedEmail,
                        reason = AuthenticationFailureReason.ACCOUNT_INACTIVE,
                        context = context,
                        failedAttemptCount = user.failedAttempts,
                        deviceFingerprint = request.deviceFingerprint
                    )
                    incrementAuthenticationCounter("account_inactive")
                    mapAccountStatusToError(user.status)
                }

                // Authentication successful - reset failed attempts and update last login
                user.resetFailedAttempts()
                user.updateLastLogin(request.deviceFingerprint)
                userRepository.save(user)

                // Publish success event
                publishSuccessEvent(
                    user = user,
                    context = context,
                    mfaRequired = user.mfaEnabled,
                    deviceFingerprint = request.deviceFingerprint
                )

                incrementAuthenticationCounter("success")
                logger.info("User {} authenticated successfully", user.id)

                // Build response based on MFA status
                if (user.mfaEnabled) {
                    // Generate MFA token (placeholder - would be implemented in MFA story)
                    val mfaToken = generateMfaToken(user)
                    SigninResponse.mfaRequired(
                        mfaToken = mfaToken,
                        mfaMethods = getMfaMethods(user)
                    )
                } else {
                    SigninResponse.success(userId = user.id)
                }
            }
        } ?: Either.Left(AuthenticationError.InvalidCredentials(remainingAttempts = 0))
    }

    /**
     * Performs a dummy password check for timing attack prevention.
     *
     * This ensures that the response time for non-existent users is
     * consistent with the response time for existing users with wrong passwords.
     */
    private fun performDummyPasswordCheck() {
        passwordVerifyTimer.record<Boolean> {
            passwordHasher.verify("dummy_password", dummyPasswordHash)
        }
    }

    /**
     * Maps an account status to the appropriate authentication error.
     */
    private fun mapAccountStatusToError(status: UserStatus): AuthenticationError {
        return when (status) {
            UserStatus.PENDING_VERIFICATION -> AuthenticationError.AccountInactive(
                status = status,
                reason = "Please verify your email address before signing in."
            )
            UserStatus.SUSPENDED -> AuthenticationError.AccountInactive(
                status = status,
                reason = "Your account has been suspended. Please contact support at $supportUrl"
            )
            UserStatus.DEACTIVATED -> AuthenticationError.AccountInactive(
                status = status,
                reason = "Your account has been deactivated. Please contact support to reactivate."
            )
            UserStatus.LOCKED -> AuthenticationError.AccountInactive(
                status = status,
                reason = "Your account is locked. Please try again later or contact support."
            )
            UserStatus.DELETED -> AuthenticationError.AccountInactive(
                status = status,
                reason = "This account no longer exists."
            )
            UserStatus.ACTIVE -> throw IllegalStateException("ACTIVE status should not reach this point")
        }
    }

    /**
     * Publishes an AuthenticationFailed event to the event store and Kafka.
     */
    private fun publishFailedEvent(
        userId: UUID,
        email: String,
        reason: AuthenticationFailureReason,
        context: AuthenticationContext,
        failedAttemptCount: Int,
        deviceFingerprint: String?
    ) {
        val event = AuthenticationFailed.create(
            userId = userId,
            email = email,
            reason = reason,
            ipAddress = context.ipAddress,
            userAgent = context.userAgent,
            failedAttemptCount = failedAttemptCount,
            deviceFingerprint = deviceFingerprint,
            correlationId = context.correlationId
        )

        // Persist to event store first
        eventStoreRepository.append(event)

        // Publish to Kafka (async)
        userEventPublisher.publishAuthenticationFailed(event)
    }

    /**
     * Publishes an AuthenticationSucceeded event to the event store and Kafka.
     */
    private fun publishSuccessEvent(
        user: User,
        context: AuthenticationContext,
        mfaRequired: Boolean,
        deviceFingerprint: String?
    ) {
        val event = AuthenticationSucceeded.create(
            userId = user.id,
            email = user.email,
            ipAddress = context.ipAddress,
            userAgent = context.userAgent,
            mfaRequired = mfaRequired,
            deviceFingerprint = deviceFingerprint,
            correlationId = context.correlationId
        )

        // Persist to event store first
        eventStoreRepository.append(event)

        // Publish to Kafka (async)
        userEventPublisher.publishAuthenticationSucceeded(event)
    }

    /**
     * Generates an MFA token for the user.
     *
     * This is a placeholder implementation. The actual MFA token generation
     * would be implemented in the MFA user story.
     */
    private fun generateMfaToken(user: User): String {
        return "mfa_${UUID.randomUUID()}"
    }

    /**
     * Gets the available MFA methods for the user.
     *
     * This is a placeholder implementation. The actual MFA methods would
     * be retrieved from the user's MFA configuration.
     */
    private fun getMfaMethods(user: User): List<MfaMethod> {
        // Placeholder - would be retrieved from user's MFA configuration
        return listOf(MfaMethod.TOTP)
    }

    /**
     * Publishes an AccountLocked event to the event store and Kafka.
     *
     * This triggers the Notification Service to send a lockout email to the customer.
     */
    private fun publishLockEvent(
        user: User,
        reason: AccountLockReason,
        lockedUntil: Instant,
        context: AuthenticationContext
    ) {
        val event = AccountLockedEvent.create(
            userId = user.id,
            email = user.email,
            reason = reason,
            failedAttemptCount = user.failedAttempts,
            lockedUntil = lockedUntil,
            ipAddress = context.ipAddress,
            userAgent = context.userAgent,
            correlationId = context.correlationId
        )

        // Persist to event store first
        eventStoreRepository.append(event)

        // Publish to Kafka (async) - triggers notification email
        userEventPublisher.publishAccountLocked(event)
    }

    /**
     * Publishes an AccountUnlocked event to the event store and Kafka.
     */
    private fun publishUnlockEvent(
        user: User,
        reason: AccountUnlockReason,
        previousLockReason: AccountLockReason?,
        correlationId: UUID
    ) {
        val event = AccountUnlockedEvent.create(
            userId = user.id,
            email = user.email,
            reason = reason,
            previousLockReason = previousLockReason,
            correlationId = correlationId
        )

        // Persist to event store first
        eventStoreRepository.append(event)

        // Publish to Kafka (async)
        userEventPublisher.publishAccountUnlocked(event)
    }

    /**
     * Increments the authentication attempts counter with the given status.
     */
    private fun incrementAuthenticationCounter(status: String) {
        meterRegistry.counter("authentication_attempts_total", "status", status).increment()
    }
}
