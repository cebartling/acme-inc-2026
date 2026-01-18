package com.acme.identity.api.v1

import com.acme.identity.api.v1.dto.ErrorResponse
import com.acme.identity.api.v1.dto.ResendVerificationRequest
import com.acme.identity.api.v1.dto.ResendVerificationResponse
import com.acme.identity.application.ResendError
import com.acme.identity.application.ResendVerificationUseCase
import com.acme.identity.application.VerificationError
import com.acme.identity.application.VerifyEmailUseCase
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * REST controller for email verification endpoints.
 *
 * Provides the public API for email verification and resend functionality.
 * All endpoints are versioned under `/api/v1/users`.
 *
 * The verify endpoint redirects users to appropriate pages based on the
 * verification result, rather than returning JSON responses.
 *
 * @property verifyEmailUseCase The use case for email verification.
 * @property resendVerificationUseCase The use case for resending verification emails.
 * @property frontendBaseUrl Base URL for frontend redirects.
 */
@RestController
@RequestMapping("/api/v1/users")
class VerificationController(
    private val verifyEmailUseCase: VerifyEmailUseCase,
    private val resendVerificationUseCase: ResendVerificationUseCase,
    @Value("\${identity.frontend.base-url:https://www.acme.com}")
    private val frontendBaseUrl: String
) {
    private val logger = LoggerFactory.getLogger(VerificationController::class.java)

    /**
     * Verifies a user's email address using a verification token.
     *
     * This endpoint is called when the user clicks the verification link
     * in their email. It validates the token and redirects to the appropriate
     * frontend page based on the result.
     *
     * @param token The verification token from the email link.
     * @param correlationId Optional correlation ID for distributed tracing.
     * @return 302 Redirect to login page on success, or to resend page on failure.
     */
    @GetMapping("/verify")
    fun verifyEmail(
        @RequestParam("token") token: String,
        @RequestHeader("X-Correlation-ID", required = false) correlationId: String?
    ): ResponseEntity<Void> {
        val corrId = correlationId?.let {
            try {
                UUID.fromString(it)
            } catch (_: IllegalArgumentException) {
                UUID.randomUUID()
            }
        } ?: UUID.randomUUID()

        return verifyEmailUseCase.execute(token, corrId).fold(
            ifLeft = { error ->
                when (error) {
                    is VerificationError.ExpiredToken -> {
                        logger.info("Verification failed: expired token")
                        redirectTo("$frontendBaseUrl/verify/resend?error=expired")
                    }
                    is VerificationError.InvalidToken -> {
                        logger.info("Verification failed: invalid token")
                        redirectTo("$frontendBaseUrl/verify/resend?error=invalid")
                    }
                    is VerificationError.AlreadyVerified -> {
                        logger.info("Verification attempt for already-verified user")
                        redirectTo("$frontendBaseUrl/login?already_verified=true")
                    }
                    is VerificationError.InternalError -> {
                        logger.error("Verification failed with error: {}", error.message)
                        redirectTo("$frontendBaseUrl/verify/resend?error=invalid")
                    }
                }
            },
            ifRight = { success ->
                logger.info("Email verified successfully for user: {}", success.userId)
                redirectTo("$frontendBaseUrl/login?verified=true")
            }
        )
    }

    /**
     * Resends a verification email to the specified email address.
     *
     * Rate limited to 3 requests per hour per email address.
     * For security, the response is the same whether the email exists or not.
     *
     * @param request The resend request containing the email address.
     * @param httpRequest The HTTP servlet request (for IP extraction).
     * @param correlationId Optional correlation ID for distributed tracing.
     * @return 200 OK with message on success, 429 Too Many Requests if rate limited.
     */
    @PostMapping("/verify/resend")
    fun resendVerification(
        @Valid @RequestBody request: ResendVerificationRequest,
        httpRequest: HttpServletRequest,
        @RequestHeader("X-Correlation-ID", required = false) correlationId: String?
    ): ResponseEntity<Any> {
        val clientIp = getClientIp(httpRequest)
        val corrId = correlationId?.let {
            try {
                UUID.fromString(it)
            } catch (_: IllegalArgumentException) {
                UUID.randomUUID()
            }
        } ?: UUID.randomUUID()

        return resendVerificationUseCase.execute(request.email, clientIp, corrId).fold(
            ifLeft = { error ->
                when (error) {
                    is ResendError.RateLimited -> {
                        val retryAfterSeconds = Duration.between(Instant.now(), error.retryAfter).seconds
                        val minutes = formatDuration(retryAfterSeconds)
                        val minuteLabel = if (minutes == 1L) "minute" else "minutes"
                        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                            .header(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString())
                            .body(
                                ErrorResponse(
                                    error = "RATE_LIMIT_EXCEEDED",
                                    message = "Too many requests. Please try again in $minutes $minuteLabel."
                                )
                            )
                    }
                    is ResendError.InternalError -> {
                        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                            ErrorResponse(
                                error = "RESEND_ERROR",
                                message = error.message
                            )
                        )
                    }
                }
            },
            ifRight = { success ->
                ResponseEntity.ok(
                    ResendVerificationResponse(
                        message = success.message,
                        requestsRemaining = success.requestsRemaining
                    )
                )
            }
        )
    }

    /**
     * Creates a redirect response to the specified URL.
     *
     * @param url The URL to redirect to.
     * @return A 302 Found response with Location header.
     */
    private fun redirectTo(url: String): ResponseEntity<Void> {
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(url))
            .build()
    }

    /**
     * Extracts the client IP address from the request.
     *
     * Handles X-Forwarded-For header for clients behind proxies/load balancers.
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
     * Formats seconds as minutes for user-friendly display.
     *
     * @param seconds The duration in seconds.
     * @return The duration in minutes (rounded up).
     */
    private fun formatDuration(seconds: Long): Long {
        return (seconds + 59) / 60 // Round up to next minute
    }
}
