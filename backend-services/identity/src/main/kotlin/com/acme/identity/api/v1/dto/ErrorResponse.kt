package com.acme.identity.api.v1.dto

import java.time.Instant

/**
 * Standard error response DTO for API errors.
 *
 * Provides a consistent error format across all API endpoints,
 * including error codes, human-readable messages, and optional
 * field-level validation details.
 *
 * @property error A machine-readable error code (e.g., "VALIDATION_ERROR", "DUPLICATE_EMAIL").
 * @property message A human-readable error description.
 * @property timestamp When the error occurred. Defaults to the current time.
 * @property details Optional list of field-level validation errors.
 *                   Present when the error is "VALIDATION_ERROR".
 */
data class ErrorResponse(
    val error: String,
    val message: String,
    val timestamp: Instant = Instant.now(),
    val details: List<FieldError>? = null
)

/**
 * Represents a validation error for a specific field.
 *
 * Used within [ErrorResponse.details] to provide granular feedback
 * about which fields failed validation and why.
 *
 * @property field The name of the field that failed validation.
 * @property message A description of the validation failure.
 */
data class FieldError(
    val field: String,
    val message: String
)
