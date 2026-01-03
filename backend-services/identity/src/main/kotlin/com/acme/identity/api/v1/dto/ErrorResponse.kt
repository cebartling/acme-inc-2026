package com.acme.identity.api.v1.dto

import java.time.Instant

data class ErrorResponse(
    val error: String,
    val message: String,
    val timestamp: Instant = Instant.now(),
    val details: List<FieldError>? = null
)

data class FieldError(
    val field: String,
    val message: String
)
