package com.acme.identity.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * JPA entity representing a user in the identity system.
 *
 * This entity stores core user identity information including credentials,
 * profile data, and consent records. The password is never stored in plain
 * text; only the Argon2id hash is persisted.
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
