package com.acme.identity.domain

import jakarta.persistence.*
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * JPA entity representing a user in the identity system.
 *
 * This entity stores core user identity information including credentials,
 * profile data, consent records, and authentication state. The password is
 * never stored in plain text; only the Argon2id hash is persisted.
 *
 * @property id Unique identifier for the user (UUID v7, time-ordered).
 * @property email User's email address, used as the primary login credential.
 * @property passwordHash Argon2id hash of the user's password.
 * @property firstName User's first/given name.
 * @property lastName User's last/family name.
 * @property status Current lifecycle status of the user account.
 * @property tosAcceptedAt Timestamp when the user accepted the Terms of Service.
 * @property marketingOptIn Whether the user has opted in to marketing communications.
 * @property registrationSource The channel through which the user registered.
 * @property emailVerified Whether the user's email has been verified.
 * @property verifiedAt Timestamp when the user's email was verified.
 * @property failedAttempts Number of consecutive failed signin attempts.
 * @property lockedUntil Timestamp until which the account is locked, null if not locked.
 * @property mfaEnabled Whether multi-factor authentication is enabled.
 * @property lastLoginAt Timestamp of the last successful signin.
 * @property lastDeviceFingerprint Device fingerprint from the last signin attempt.
 * @property createdAt Timestamp when the user record was created.
 * @property updatedAt Timestamp when the user record was last modified.
 */
@Entity
@Table(name = "users")
class User(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "email", nullable = false, unique = true)
    val email: String,

    @Column(name = "password_hash", nullable = false)
    val passwordHash: String,

    @Column(name = "first_name", nullable = false, length = 50)
    val firstName: String,

    @Column(name = "last_name", nullable = false, length = 50)
    val lastName: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    val status: UserStatus = UserStatus.PENDING_VERIFICATION,

    @Column(name = "tos_accepted_at", nullable = false)
    val tosAcceptedAt: Instant,

    @Column(name = "marketing_opt_in", nullable = false)
    val marketingOptIn: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_source", nullable = false, length = 20)
    val registrationSource: RegistrationSource,

    @Column(name = "email_verified", nullable = false)
    var emailVerified: Boolean = false,

    @Column(name = "verified_at")
    var verifiedAt: Instant? = null,

    @Column(name = "failed_attempts", nullable = false)
    var failedAttempts: Int = 0,

    @Column(name = "locked_until")
    var lockedUntil: Instant? = null,

    @Column(name = "mfa_enabled", nullable = false)
    var mfaEnabled: Boolean = false,

    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null,

    @Column(name = "last_device_fingerprint")
    var lastDeviceFingerprint: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    /**
     * Returns the user's ID wrapped in a type-safe [UserId] value class.
     *
     * @return The user's identifier as a [UserId].
     */
    fun getUserId(): UserId = UserId(id)

    /**
     * Marks the user's email as verified and updates the verification timestamp.
     *
     * This method should be called when the user successfully verifies their email
     * by clicking the verification link.
     */
    fun markEmailAsVerified() {
        emailVerified = true
        verifiedAt = Instant.now()
        updatedAt = Instant.now()
    }

    /**
     * Activates the user account by setting the status to ACTIVE.
     *
     * This method should be called after email verification or other
     * activation methods to enable full access to the platform.
     *
     * @return The new user with updated status.
     */
    fun activate(): User {
        return User(
            id = id,
            email = email,
            passwordHash = passwordHash,
            firstName = firstName,
            lastName = lastName,
            status = UserStatus.ACTIVE,
            tosAcceptedAt = tosAcceptedAt,
            marketingOptIn = marketingOptIn,
            registrationSource = registrationSource,
            emailVerified = emailVerified,
            verifiedAt = verifiedAt,
            failedAttempts = failedAttempts,
            lockedUntil = lockedUntil,
            mfaEnabled = mfaEnabled,
            lastLoginAt = lastLoginAt,
            lastDeviceFingerprint = lastDeviceFingerprint,
            createdAt = createdAt,
            updatedAt = Instant.now()
        )
    }

    /**
     * Checks if the user account is pending verification.
     *
     * @return `true` if the user's status is PENDING_VERIFICATION.
     */
    fun isPendingVerification(): Boolean = status == UserStatus.PENDING_VERIFICATION

    /**
     * Checks if the user account is active.
     *
     * @return `true` if the user's status is ACTIVE.
     */
    fun isActive(): Boolean = status == UserStatus.ACTIVE

    /**
     * Checks if the user account is currently locked.
     *
     * An account is locked if the status is LOCKED and the lockout period
     * has not yet expired.
     *
     * @return `true` if the account is locked and the lockout period has not expired.
     */
    fun isLocked(): Boolean {
        return status == UserStatus.LOCKED && lockedUntil?.isAfter(Instant.now()) == true
    }

    /**
     * Checks if the account can be signed into (is active or has expired lockout).
     *
     * @return `true` if the account status allows signin.
     */
    fun canSignin(): Boolean {
        return status == UserStatus.ACTIVE || (status == UserStatus.LOCKED && !isLocked())
    }

    /**
     * Increments the failed attempts counter.
     *
     * This should be called when a signin attempt fails due to invalid credentials.
     * Does not persist changes - caller must save the user.
     */
    fun incrementFailedAttempts() {
        failedAttempts++
        updatedAt = Instant.now()
    }

    /**
     * Resets the failed attempts counter to zero.
     *
     * This should be called after a successful signin.
     * Does not persist changes - caller must save the user.
     */
    fun resetFailedAttempts() {
        failedAttempts = 0
        updatedAt = Instant.now()
    }

    /**
     * Locks the user account for the specified duration.
     *
     * Sets the status to LOCKED and records the lockout expiration time.
     * Does not persist changes - caller must save the user.
     *
     * @param duration The duration for which the account should be locked.
     */
    fun lock(duration: Duration) {
        lockedUntil = Instant.now().plus(duration)
        updatedAt = Instant.now()
    }

    /**
     * Unlocks the user account.
     *
     * Clears the lockout timestamp and resets failed attempts.
     * Does not change the status - that should be handled separately.
     * Does not persist changes - caller must save the user.
     */
    fun unlock() {
        lockedUntil = null
        failedAttempts = 0
        updatedAt = Instant.now()
    }

    /**
     * Updates the last login timestamp and optionally the device fingerprint.
     *
     * This should be called after a successful signin.
     * Does not persist changes - caller must save the user.
     *
     * @param deviceFingerprint Optional device fingerprint from the signin request.
     */
    fun updateLastLogin(deviceFingerprint: String? = null) {
        lastLoginAt = Instant.now()
        if (deviceFingerprint != null) {
            lastDeviceFingerprint = deviceFingerprint
        }
        updatedAt = Instant.now()
    }

    /**
     * Calculates the remaining signin attempts before account lockout.
     *
     * @param maxAttempts The maximum number of failed attempts allowed.
     * @return The number of remaining attempts, or 0 if the limit has been reached.
     */
    fun remainingAttempts(maxAttempts: Int): Int {
        return maxOf(0, maxAttempts - failedAttempts)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
