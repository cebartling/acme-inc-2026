package com.acme.identity.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Available MFA methods for authentication.
 */
enum class MfaMethod {
    /** Time-based One-Time Password (TOTP) via authenticator app. */
    TOTP,

    /** One-time code sent via SMS. */
    SMS,

    /** One-time code sent via email. */
    EMAIL
}

/**
 * JPA entity representing an active MFA challenge during authentication.
 *
 * When a user with MFA enabled signs in with valid credentials, an MFA challenge
 * is created and must be completed within the expiration window. The challenge
 * tracks the number of verification attempts to prevent brute force attacks.
 *
 * @property id Unique identifier for the challenge (UUID v7, time-ordered).
 * @property userId The ID of the user this challenge belongs to.
 * @property token Unique token used to identify this challenge in API calls.
 * @property method The MFA method being used (TOTP, SMS, EMAIL).
 * @property expiresAt Timestamp when this challenge expires (5 minutes from creation).
 * @property attempts Number of verification attempts made.
 * @property maxAttempts Maximum allowed attempts before challenge is invalidated.
 * @property codeHash SHA-256 hash of the SMS verification code (null for TOTP).
 * @property lastSentAt Timestamp when the SMS code was last sent (null for TOTP).
 * @property createdAt Timestamp when the challenge was created.
 */
@Entity
@Table(name = "mfa_challenges")
class MfaChallenge(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "token", nullable = false, unique = true, length = 100)
    val token: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    val method: MfaMethod,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "attempts", nullable = false)
    var attempts: Int = 0,

    @Column(name = "max_attempts", nullable = false)
    val maxAttempts: Int = 3,

    @Column(name = "code_hash", length = 64)
    var codeHash: String? = null,

    @Column(name = "last_sent_at")
    var lastSentAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
) {
    /**
     * Checks if this challenge has expired.
     *
     * @return `true` if the current time is past the expiration time.
     */
    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)

    /**
     * Checks if the maximum number of attempts has been reached.
     *
     * @return `true` if attempts >= maxAttempts.
     */
    fun hasExceededMaxAttempts(): Boolean = attempts >= maxAttempts

    /**
     * Checks if this challenge is still valid for verification.
     *
     * @return `true` if not expired and under max attempts.
     */
    fun isValid(): Boolean = !isExpired() && !hasExceededMaxAttempts()

    /**
     * Increments the attempt counter.
     */
    fun incrementAttempts() {
        attempts++
    }

    /**
     * Returns the number of remaining attempts.
     *
     * @return The number of attempts remaining, minimum 0.
     */
    fun remainingAttempts(): Int = maxOf(0, maxAttempts - attempts)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MfaChallenge) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    companion object {
        /** Default challenge expiration: 5 minutes. */
        const val DEFAULT_EXPIRY_SECONDS = 300L

        /** Default maximum attempts. */
        const val DEFAULT_MAX_ATTEMPTS = 3

        /**
         * Creates a new MFA challenge for the given user.
         *
         * @param userId The ID of the user.
         * @param method The MFA method to use.
         * @param expirySeconds Challenge expiration in seconds (default: 300).
         * @param maxAttempts Maximum verification attempts (default: 3).
         * @param codeHash SHA-256 hash of SMS code (required for SMS method).
         * @return A new MfaChallenge instance.
         */
        fun create(
            userId: UUID,
            method: MfaMethod,
            expirySeconds: Long = DEFAULT_EXPIRY_SECONDS,
            maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
            codeHash: String? = null
        ): MfaChallenge {
            val now = Instant.now()
            return MfaChallenge(
                id = UUID.randomUUID(),
                userId = userId,
                token = "mfa_${UUID.randomUUID()}",
                method = method,
                expiresAt = now.plusSeconds(expirySeconds),
                attempts = 0,
                maxAttempts = maxAttempts,
                codeHash = codeHash,
                lastSentAt = if (method == MfaMethod.SMS) now else null
            )
        }
    }
}
