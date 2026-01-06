package com.acme.customer.api.v1.dto

import java.time.Instant

/**
 * Standard error response DTO for API errors.
 */
data class ErrorResponse(
    val timestamp: Instant = Instant.now(),
    val status: Int,
    val error: String,
    val message: String,
    val path: String? = null,
    val details: List<FieldError>? = null
)

/**
 * Field-level validation error.
 */
data class FieldError(
    val field: String,
    val message: String,
    val rejectedValue: Any? = null
)
