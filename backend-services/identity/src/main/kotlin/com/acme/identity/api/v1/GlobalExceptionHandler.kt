package com.acme.identity.api.v1

import com.acme.identity.api.v1.dto.ErrorResponse
import com.acme.identity.api.v1.dto.FieldError
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

/**
 * Global exception handler for REST API endpoints.
 *
 * Provides consistent error responses across all API endpoints by
 * catching and transforming exceptions into standardized [ErrorResponse] objects.
 *
 * Error responses include:
 * - A machine-readable error code
 * - A human-readable message
 * - Optional field-level validation details
 * - A timestamp
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /**
     * Handles Bean Validation errors from @Valid annotations.
     *
     * @param ex The validation exception containing field errors.
     * @return 400 Bad Request with detailed validation error information.
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val fieldErrors = ex.bindingResult.fieldErrors.map { error ->
            FieldError(
                field = error.field,
                message = error.defaultMessage ?: "Invalid value"
            )
        }

        logger.debug("Validation failed: {}", fieldErrors)

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                error = "VALIDATION_ERROR",
                message = "Request validation failed",
                details = fieldErrors
            )
        )
    }

    /**
     * Handles malformed or missing request body.
     *
     * @param ex The exception thrown when JSON parsing fails.
     * @return 400 Bad Request indicating the request body is invalid.
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadable(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        logger.debug("Message not readable: {}", ex.message)

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                error = "INVALID_REQUEST",
                message = "Request body is invalid or missing"
            )
        )
    }

    /**
     * Handles illegal argument exceptions.
     *
     * @param ex The exception thrown for invalid arguments.
     * @return 400 Bad Request with the error message.
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        logger.warn("Illegal argument: {}", ex.message)

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                error = "INVALID_ARGUMENT",
                message = ex.message ?: "Invalid argument provided"
            )
        )
    }

    /**
     * Handles a [ResponseStatusException] with its own status, e.g. the test API's 403 for a
     * missing or wrong `X-Test-Api-Key` (PIN-332), instead of the generic 500.
     *
     * @param ex The exception, carrying the status and reason to return.
     * @return The exception's status, with the status name as the error code.
     */
    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(ex: ResponseStatusException): ResponseEntity<ErrorResponse> {
        logger.warn("Request refused with {}: {}", ex.statusCode, ex.reason)

        return ResponseEntity.status(ex.statusCode).headers(ex.headers).body(
            ErrorResponse(
                error = HttpStatus.resolve(ex.statusCode.value())?.name ?: ex.statusCode.toString(),
                message = ex.reason ?: "Request refused"
            )
        )
    }

    /**
     * Handles all unhandled exceptions as a fallback.
     *
     * Logs the full exception for debugging but returns a generic
     * error message to avoid exposing internal details.
     *
     * @param ex The unhandled exception.
     * @return 500 Internal Server Error with a generic message.
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error occurred", ex)

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                error = "INTERNAL_ERROR",
                message = "An unexpected error occurred"
            )
        )
    }
}
