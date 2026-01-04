package com.acme.identity.api.v1.dto

import com.acme.identity.domain.UserStatus
import java.time.Instant
import java.util.UUID

/**
 * Response DTO returned after successful user registration.
 *
 * Contains the essential information about the newly created user account
 * that the client needs to proceed with the registration flow.
 *
 * @property userId The unique identifier (UUID v7) assigned to the new user.
 * @property email The user's email address (normalized to lowercase).
 * @property status The initial status of the account, typically [UserStatus.PENDING_VERIFICATION].
 * @property createdAt Timestamp when the user account was created.
 */
data class RegisterUserResponse(
    val userId: UUID,
    val email: String,
    val status: UserStatus,
    val createdAt: Instant
)
