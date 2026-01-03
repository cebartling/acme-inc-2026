package com.acme.identity.api.v1.dto

import com.acme.identity.domain.UserStatus
import java.time.Instant
import java.util.UUID

data class RegisterUserResponse(
    val userId: UUID,
    val email: String,
    val status: UserStatus,
    val createdAt: Instant
)
