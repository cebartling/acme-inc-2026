package com.acme.identity.api.v1.dto

import jakarta.validation.constraints.*
import java.time.Instant

/**
 * Request DTO for user registration.
 *
 * Contains all the information required to create a new user account.
 * All fields are validated using Jakarta Bean Validation annotations.
 *
 * @property email The user's email address. Must be a valid email format
 *                 and not exceed 255 characters.
 * @property password The user's chosen password. Must be 8-128 characters
 *                    and contain at least one uppercase letter, one lowercase
 *                    letter, one digit, and one special character.
 * @property firstName The user's first name. Must be 1-50 characters.
 * @property lastName The user's last name. Must be 1-50 characters.
 * @property tosAccepted Must be `true` to indicate acceptance of Terms of Service.
 * @property tosAcceptedAt Timestamp when the user accepted the Terms of Service.
 * @property marketingOptIn Whether the user opts in to marketing communications.
 *                          Defaults to `false`.
 */
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
