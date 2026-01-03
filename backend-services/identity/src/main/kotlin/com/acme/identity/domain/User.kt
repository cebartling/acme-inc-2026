package com.acme.identity.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

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

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    fun getUserId(): UserId = UserId(id)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
