package com.acme.identity.api.v1

import com.acme.identity.api.v1.dto.SigninErrorResponse
import com.acme.identity.api.v1.dto.SigninRequest
import com.acme.identity.application.AuthenticateUserUseCase
import com.acme.identity.application.AuthenticationContext
import com.acme.identity.application.AuthenticationError
import com.acme.identity.domain.UserStatus
import com.acme.identity.infrastructure.security.RateLimiter
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * REST controller for authentication endpoints.
 *
 * Provides the public API for user signin and related authentication operations.
 * All endpoints are versioned under `/api/v1/auth`.
 *
 * @property authenticateUserUseCase The use case for user authentication.
 * @property rateLimiter The rate limiter for preventing brute force attacks.
 * @property supportUrl URL for customer support.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthenticationController(
    private val authenticateUserUseCase: AuthenticateUserUseCase,
    private val rateLimiter: RateLimiter,
    @Value("\${identity.support-url:https://www.acme.com/support}")
    private val supportUrl: String = "https://www.acme.com/support",
    @Value("\${identity.password-reset-url:https://www.acme.com/forgot-password}")
    private val passwordResetUrl: String = "https://www.acme.com/forgot-password"
) {
    private val logger = LoggerFactory.getLogger(AuthenticationController::class.java)

    /**
     * Authenticates a user with email and password.
     *
     * This endpoint validates credentials and returns either:
     * - A success response with user ID and session info
     * - An MFA required response with MFA token and available methods
     * - An error response for invalid credentials or account issues
     *
     * Rate limiting: Applied per IP address + email combination.
     *
     * Device trust: If a valid device_trust cookie is present, MFA may be bypassed.
     *
     * @param request The signin request containing credentials.
     * @param httpRequest The HTTP servlet request (for IP extraction and cookies).
     * @param correlationId Optional correlation ID for distributed tracing.
     * @param deviceTrustCookie Optional device trust token from cookie.
     * @return 200 OK with signin response on success or MFA required,
     *         401 Unauthorized for invalid credentials,
     *         403 Forbidden for inactive accounts,
     *         423 Locked for locked accounts,
     *         429 Too Many Requests if rate limited.
     */
    @PostMapping("/signin")
    fun signin(
        @Valid @RequestBody request: SigninRequest,
        httpRequest: HttpServletRequest,
        @RequestHeader("X-Correlation-ID", required = false) correlationId: String?,
        @CookieValue(value = "device_trust", required = false) deviceTrustCookie: String?
    ): ResponseEntity<Any> {
        val clientIp = getClientIp(httpRequest)
        val userAgent = httpRequest.getHeader("User-Agent") ?: "unknown"
        val rateLimitKey = "$clientIp:${request.email.lowercase()}"

        // Rate limiting check
        if (!rateLimiter.tryAcquire(rateLimitKey)) {
            logger.warn("Rate limit exceeded for IP: {} email: {}", clientIp, request.email)
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(
                    SigninErrorResponse(
                        error = "RATE_LIMITED",
                        message = "Too many signin attempts. Please try again later."
                    )
                )
        }

        // Create request with device trust token from cookie
        val requestWithDeviceTrust = request.copy(deviceTrustToken = deviceTrustCookie)

        val context = AuthenticationContext(
            ipAddress = clientIp,
            userAgent = userAgent,
            correlationId = correlationId?.let { UUID.fromString(it) } ?: UUID.randomUUID()
        )

        return authenticateUserUseCase.execute(requestWithDeviceTrust, context).fold(
            ifLeft = { error ->
                mapErrorToResponse(error)
            },
            ifRight = { response ->
                ResponseEntity.ok(response)
            }
        )
    }

    /**
     * Maps an authentication error to the appropriate HTTP response.
     */
    private fun mapErrorToResponse(error: AuthenticationError): ResponseEntity<Any> {
        return when (error) {
            is AuthenticationError.InvalidCredentials -> {
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    SigninErrorResponse(
                        error = "INVALID_CREDENTIALS",
                        message = "Invalid email or password",
                        remainingAttempts = error.remainingAttempts
                    )
                )
            }
            is AuthenticationError.AccountInactive -> {
                ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    SigninErrorResponse(
                        error = "ACCOUNT_INACTIVE",
                        message = "Account is not active",
                        reason = error.status.name,
                        supportUrl = when (error.status) {
                            UserStatus.SUSPENDED, UserStatus.DEACTIVATED -> supportUrl
                            else -> null
                        }
                    )
                )
            }
            is AuthenticationError.AccountLocked -> {
                ResponseEntity.status(HttpStatus.LOCKED).body(
                    SigninErrorResponse(
                        error = "ACCOUNT_LOCKED",
                        message = "Account is locked due to too many failed signin attempts",
                        lockedUntil = error.lockedUntil,
                        lockoutRemainingSeconds = error.lockoutRemainingSeconds,
                        supportUrl = supportUrl,
                        passwordResetUrl = passwordResetUrl
                    )
                )
            }
            is AuthenticationError.RateLimited -> {
                ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                    SigninErrorResponse(
                        error = "RATE_LIMITED",
                        message = "Too many signin attempts. Please try again later."
                    )
                )
            }
            is AuthenticationError.MfaSystemUnavailable -> {
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                    SigninErrorResponse(
                        error = "MFA_UNAVAILABLE",
                        message = error.reason,
                        supportUrl = supportUrl
                    )
                )
            }
            is AuthenticationError.SmsRateLimited -> {
                ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                    SigninErrorResponse(
                        error = "SMS_RATE_LIMITED",
                        message = "Too many SMS verification requests. Please try again later.",
                        retryAfterSeconds = error.retryAfterSeconds
                    )
                )
            }
        }
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
}
