package com.acme.identity.api.v1.dto

import jakarta.validation.constraints.*
import java.time.Instant

data class RegisterUserRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    @field:Size(max = 255, message = "Email must not exceed 255 characters")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    @field:Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@\$!%*?&])[A-Za-z\\d@\$!%*?&]{8,}\$",
        message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character"
    )
    val password: String,

    @field:NotBlank(message = "First name is required")
    @field:Size(min = 1, max = 50, message = "First name must be between 1 and 50 characters")
    val firstName: String,

    @field:NotBlank(message = "Last name is required")
    @field:Size(min = 1, max = 50, message = "Last name must be between 1 and 50 characters")
    val lastName: String,

    @field:AssertTrue(message = "Terms of service must be accepted")
    val tosAccepted: Boolean,

    @field:NotNull(message = "Terms of service acceptance timestamp is required")
    val tosAcceptedAt: Instant,

    val marketingOptIn: Boolean = false
)
