package com.acme.identity.api.v1

import arrow.core.getOrElse
import com.acme.identity.api.v1.dto.ErrorResponse
import com.acme.identity.api.v1.dto.RegisterUserRequest
import com.acme.identity.api.v1.dto.RegisterUserResponse
import com.acme.identity.application.RegistrationError
import com.acme.identity.application.RegisterUserUseCase
import com.acme.identity.domain.RegistrationSource
import com.acme.identity.infrastructure.security.RateLimiter
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * REST controller for user management endpoints.
 *
 * Provides the public API for user registration and related operations.
 * All endpoints are versioned under `/api/v1/users`.
 *
 * @property registerUserUseCase The use case for user registration.
 * @property rateLimiter The rate limiter for preventing abuse.
 */
@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val registerUserUseCase: RegisterUserUseCase,
    private val rateLimiter: RateLimiter
) {
    private val logger = LoggerFactory.getLogger(UserController::class.java)

    /**
     * Registers a new user account.
     *
     * This endpoint creates a new user with the provided information.
     * The user will be created in PENDING_VERIFICATION status and will
     * need to verify their email before gaining full access.
     *
     * Rate limiting: 5 requests per minute per IP address.
     *
     * @param request The registration request containing user details.
     * @param httpRequest The HTTP servlet request (for IP extraction).
     * @param correlationId Optional correlation ID for distributed tracing.
     * @param source Optional registration source (WEB, MOBILE, API).
     * @return 201 Created with user details on success,
     *         409 Conflict if email already exists,
     *         429 Too Many Requests if rate limited,
     *         400 Bad Request for validation errors.
     */
    @PostMapping("/register")
    fun register(
        @Valid @RequestBody request: RegisterUserRequest,
        httpRequest: HttpServletRequest,
        @RequestHeader("X-Correlation-ID", required = false) correlationId: String?,
        @RequestHeader("X-Registration-Source", required = false) source: String?
    ): ResponseEntity<Any> {
        val clientIp = getClientIp(httpRequest)

        // Rate limiting check
        if (!rateLimiter.tryAcquire(clientIp)) {
            logger.warn("Rate limit exceeded for IP: {}", clientIp)
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(
                    ErrorResponse(
                        error = "RATE_LIMIT_EXCEEDED",
                        message = "Too many registration attempts. Please try again later."
                    )
                )
        }

        val registrationSource = parseRegistrationSource(source)
        val corrId = correlationId?.let { UUID.fromString(it) } ?: UUID.randomUUID()

        return registerUserUseCase.execute(request, registrationSource, corrId).fold(
            ifLeft = { error ->
                when (error) {
                    is RegistrationError.DuplicateEmail -> ResponseEntity.status(HttpStatus.CONFLICT).body(
                        ErrorResponse(
                            error = "DUPLICATE_EMAIL",
                            message = "An account with this email already exists"
                        )
                    )
                    is RegistrationError.InternalError -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                        ErrorResponse(
                            error = "REGISTRATION_ERROR",
                            message = error.message
                        )
                    )
                }
            },
            ifRight = { response ->
                ResponseEntity.status(HttpStatus.CREATED).body(response)
            }
        )
    }

    /**
     * Extracts the client IP address from the request.
     *
     * Handles X-Forwarded-For header for clients behind proxies/load balancers.
     * Properly parses comma-separated IPs with whitespace handling.
     *
     * @param request The HTTP servlet request.
     * @return The client's IP address.
     */
    private fun getClientIp(request: HttpServletRequest): String {
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        return if (!xForwardedFor.isNullOrBlank()) {
            xForwardedFor
                .split(",")
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() }
                ?: request.remoteAddr
        } else {
            request.remoteAddr
        }
    }

    /**
     * Parses the registration source from the header value.
     *
     * @param source The source header value, or null.
     * @return The parsed [RegistrationSource], defaulting to WEB.
     */
    private fun parseRegistrationSource(source: String?): RegistrationSource {
        return when (source?.uppercase()) {
            "MOBILE" -> RegistrationSource.MOBILE
            "API" -> RegistrationSource.API
            else -> RegistrationSource.WEB
        }
    }
}
