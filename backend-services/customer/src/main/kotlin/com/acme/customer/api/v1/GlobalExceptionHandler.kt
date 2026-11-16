package com.acme.customer.api.v1

import com.acme.customer.api.v1.dto.ErrorResponse
import com.acme.customer.api.v1.dto.FieldError
import com.acme.customer.infrastructure.projection.ReadModelBehindException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/**
 * Global exception handler for REST API errors.
 *
 * Provides consistent error response formatting across all endpoints.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /**
     * Handles validation errors from @Valid annotated parameters.
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationErrors(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val fieldErrors = ex.bindingResult.fieldErrors.map { error ->
            FieldError(
                field = error.field,
                message = error.defaultMessage ?: "Invalid value",
                rejectedValue = error.rejectedValue
            )
        }

        val response = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Validation Failed",
            message = "Request validation failed",
            path = request.requestURI,
            details = fieldErrors
        )

        return ResponseEntity.badRequest().body(response)
    }

    /**
     * Handles IllegalArgumentException (e.g., invalid UUIDs).
     */
    /**
     * The client asked to read after a write (`X-Read-After`) and the read model did not catch up
     * within the wait. The write succeeded; retrying the read shortly will show it.
     */
    @ExceptionHandler(ReadModelBehindException::class)
    fun handleReadModelBehind(
        ex: ReadModelBehindException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Read model behind for {}: {}", request.requestURI, ex.message)

        val response = ErrorResponse(
            status = HttpStatus.SERVICE_UNAVAILABLE.value(),
            error = "Read Model Behind",
            message = "Your change is saved but not visible yet. Retry shortly.",
            path = request.requestURI
        )

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header("Retry-After", "1")
            .body(response)
    }

    /** A header or parameter that Spring could not convert, such as an `X-Read-After` that is not a UUID. */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(
        ex: MethodArgumentTypeMismatchException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.debug("Invalid value for {}: {}", ex.name, ex.value)

        val response = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Bad Request",
            message = "Invalid value for '${ex.name}'",
            path = request.requestURI
        )

        return ResponseEntity.badRequest().body(response)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(
        ex: IllegalArgumentException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.debug("Invalid argument: {}", ex.message)

        val response = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Bad Request",
            message = ex.message ?: "Invalid request",
            path = request.requestURI
        )

        return ResponseEntity.badRequest().body(response)
    }

    /**
     * Handles all other unexpected exceptions.
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericError(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error processing request to {}: {}", request.requestURI, ex.message, ex)

        val response = ErrorResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            error = "Internal Server Error",
            message = "An unexpected error occurred",
            path = request.requestURI
        )

        return ResponseEntity.internalServerError().body(response)
    }
}
