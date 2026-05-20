package com.acme.identity.api.v1

import com.acme.identity.api.v1.dto.ChangePasswordRequest
import com.acme.identity.api.v1.dto.ChangePasswordResponse
import com.acme.identity.api.v1.dto.ErrorResponse
import com.acme.identity.api.v1.dto.LogoutAllResponse
import com.acme.identity.api.v1.dto.LogoutResponse
import com.acme.identity.api.v1.dto.PasswordRequirementsErrorResponse
import com.acme.identity.api.v1.dto.PasswordResetConfirmRequest
import com.acme.identity.api.v1.dto.PasswordResetConfirmResponse
import com.acme.identity.api.v1.dto.PasswordResetRequest
import com.acme.identity.api.v1.dto.PasswordResetResponse
import com.acme.identity.api.v1.dto.PasswordResetTokenErrorResponse
import com.acme.identity.api.v1.dto.PasswordResetTokenValidResponse
import com.acme.identity.api.v1.dto.ReactivateAccountRequest
import com.acme.identity.api.v1.dto.ReactivateAccountResponse
import com.acme.identity.api.v1.dto.RefreshTokenResponse
import com.acme.identity.api.v1.dto.SigninErrorResponse
import com.acme.identity.api.v1.dto.SigninRequest
import com.acme.identity.api.v1.dto.SigninStatus
import com.acme.identity.application.AuthenticateUserUseCase
import com.acme.identity.application.AuthenticationContext
import com.acme.identity.application.AuthenticationError
import com.acme.identity.application.AuthenticationSessionService
import com.acme.identity.application.ChangePasswordResult
import com.acme.identity.application.ChangePasswordUseCase
import com.acme.identity.application.ConfirmPasswordResetResult
import com.acme.identity.application.ConfirmPasswordResetUseCase
import com.acme.identity.application.PasswordResetTokenValidation
import com.acme.identity.application.ReactivateAccountUseCase
import com.acme.identity.application.RefreshResult
import com.acme.identity.application.RefreshTokensUseCase
import com.acme.identity.application.RequestPasswordResetUseCase
import com.acme.identity.application.ValidatePasswordResetTokenUseCase
import com.acme.identity.application.SessionService
import com.acme.identity.application.TokenService
import com.acme.identity.domain.UserStatus
import com.acme.identity.domain.events.SessionInvalidated
import com.acme.identity.infrastructure.security.AuthCookieBuilder
import com.acme.identity.infrastructure.security.RateLimiter
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
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
    private val changePasswordUseCase: ChangePasswordUseCase,
    private val reactivateAccountUseCase: ReactivateAccountUseCase,
    private val requestPasswordResetUseCase: RequestPasswordResetUseCase,
    private val validatePasswordResetTokenUseCase: ValidatePasswordResetTokenUseCase,
    private val confirmPasswordResetUseCase: ConfirmPasswordResetUseCase,
    private val refreshTokensUseCase: RefreshTokensUseCase,
    private val tokenService: TokenService,
    private val rateLimiter: RateLimiter,
    private val authenticationSessionService: AuthenticationSessionService,
    private val sessionService: SessionService,
    private val authCookieBuilder: AuthCookieBuilder,
    @Value("\${identity.support-url:https://www.acme.com/support}")
    private val supportUrl: String = "https://www.acme.com/support",
    @Value("\${identity.support-email:support@acme.com}")
    private val supportEmail: String = "support@acme.com",
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
                when (response.status) {
                    SigninStatus.SUCCESS -> {
                        // SUCCESS - create session, tokens, and cookies
                        val userId = response.userId
                            ?: throw IllegalStateException("userId is null in SUCCESS response")

                        authenticationSessionService.createAuthenticatedSession(
                            userId = userId,
                            ipAddress = context.ipAddress,
                            userAgent = context.userAgent,
                            deviceFingerprint = request.deviceFingerprint,
                            rememberDevice = request.rememberMe,
                            mfaUsed = false,
                            mfaMethod = null,
                            correlationId = context.correlationId
                        ).body(response)
                    }
                    SigninStatus.MFA_REQUIRED -> {
                        // MFA required - return response as-is (no session yet)
                        ResponseEntity.ok(response)
                    }
                }
            }
        )
    }

    /**
     * Requests reactivation of a DEACTIVATED account.
     *
     * The endpoint always responds 200 with a generic message regardless of
     * whether the email exists, whether the account is in the right state, or
     * whether the password is correct. This prevents enumeration of which
     * accounts are deactivated. When all checks pass, a single-use
     * reactivation token is generated and a [ReactivationRequested] event is
     * published so the notification service can deliver the email.
     */
    @PostMapping("/reactivate")
    fun reactivate(
        @Valid @RequestBody request: ReactivateAccountRequest,
        @RequestHeader("X-Correlation-ID", required = false) correlationId: String?
    ): ResponseEntity<ReactivateAccountResponse> {
        val corrId = correlationId?.let { UUID.fromString(it) } ?: UUID.randomUUID()
        reactivateAccountUseCase.execute(
            email = request.email,
            password = request.password,
            correlationId = corrId
        )
        return ResponseEntity.ok(ReactivateAccountResponse())
    }

    /**
     * Initiates a password reset for the given email.
     *
     * Always returns 200 with a generic message regardless of whether the
     * email maps to a real account (no enumeration). Rate-limited to 3
     * requests per hour per email; rate-limited requests still produce
     * the same 200 response.
     */
    @PostMapping("/password-reset")
    fun requestPasswordReset(
        @Valid @RequestBody request: PasswordResetRequest,
        httpRequest: HttpServletRequest,
        @RequestHeader("X-Correlation-ID", required = false) correlationId: String?
    ): ResponseEntity<PasswordResetResponse> {
        val corrId = correlationId?.let { UUID.fromString(it) } ?: UUID.randomUUID()
        requestPasswordResetUseCase.execute(
            email = request.email,
            ipAddress = getClientIp(httpRequest),
            correlationId = corrId
        )
        return ResponseEntity.ok(PasswordResetResponse())
    }

    /**
     * Validates a password-reset token without consuming it. Used by the
     * frontend to decide whether to render the new-password form or an
     * expired-link message.
     */
    @GetMapping("/password-reset/{token}")
    fun validatePasswordResetToken(
        @PathVariable("token") token: String
    ): ResponseEntity<Any> {
        return when (val result = validatePasswordResetTokenUseCase.execute(token)) {
            is PasswordResetTokenValidation.Valid ->
                ResponseEntity.ok(
                    PasswordResetTokenValidResponse(
                        valid = true,
                        expiresIn = result.expiresInSeconds
                    )
                )
            PasswordResetTokenValidation.Expired,
            PasswordResetTokenValidation.AlreadyUsed,
            PasswordResetTokenValidation.Invalid ->
                ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(PasswordResetTokenErrorResponse())
        }
    }

    /**
     * Completes a password reset. On success the user's password is
     * updated, all sessions are invalidated, all device trusts are
     * revoked, and any account lockout is cleared.
     */
    @PostMapping("/password-reset/confirm")
    fun confirmPasswordReset(
        @Valid @RequestBody request: PasswordResetConfirmRequest,
        httpRequest: HttpServletRequest,
        @RequestHeader("X-Correlation-ID", required = false) correlationId: String?
    ): ResponseEntity<Any> {
        val corrId = correlationId?.let { UUID.fromString(it) } ?: UUID.randomUUID()
        return when (val result = confirmPasswordResetUseCase.execute(
            token = request.token,
            newPassword = request.newPassword,
            ipAddress = getClientIp(httpRequest),
            correlationId = corrId
        )) {
            is ConfirmPasswordResetResult.Success ->
                ResponseEntity.ok(
                    PasswordResetConfirmResponse(
                        sessionsInvalidated = result.sessionsInvalidated,
                        deviceTrustsRevoked = result.deviceTrustsRevoked
                    )
                )
            ConfirmPasswordResetResult.InvalidToken,
            ConfirmPasswordResetResult.TokenExpired,
            ConfirmPasswordResetResult.TokenAlreadyUsed ->
                ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(PasswordResetTokenErrorResponse())
            is ConfirmPasswordResetResult.PasswordRequirementsNotMet ->
                ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(PasswordRequirementsErrorResponse(requirements = result.requirements))
        }
    }

    /**
     * Rotates an authenticated session's access + refresh tokens.
     *
     * The endpoint reads the `refresh_token` HttpOnly cookie, validates the
     * JWT, confirms the referenced session still exists in Redis, and that
     * the JWT's `tokenFamily` claim matches the session's current
     * tokenFamily. On success it issues a new pair of tokens (each with a
     * new `tokenFamily` so the previous refresh token can never be replayed)
     * and writes them as `Set-Cookie` headers. On any failure it returns
     * 401 with all auth cookies cleared, so the client falls back to a
     * fresh signin.
     *
     * Failure mapping (per US-0003-12 AC-07):
     * - `TOKEN_REUSE_DETECTED` — refresh JWT's `tokenFamily` didn't match
     *   the session's current family. The use case has already invalidated
     *   every session for the user (OWASP) and published a
     *   `TokenReuseDetected` event before this branch fires. Distinct
     *   error code so client-side analytics / future security UX can
     *   surface the reuse signal; the spec explicitly calls for this code.
     * - `TOKEN_EXPIRED` — every other failure (missing cookie, invalid
     *   JWT, evicted session). Collapsed deliberately so the response
     *   doesn't disclose session-state details.
     *
     * @param refreshToken Optional refresh-token JWT from the cookie.
     * @return 200 OK with `RefreshTokenResponse` and rotated `Set-Cookie`
     *         headers on success, 401 Unauthorized with cleared cookies on
     *         any failure.
     */
    @PostMapping("/refresh")
    fun refresh(
        @CookieValue(value = "refresh_token", required = false) refreshToken: String?
    ): ResponseEntity<Any> {
        return when (val result = refreshTokensUseCase.execute(refreshToken)) {
            is RefreshResult.Success -> {
                val builder = ResponseEntity.ok()
                builder.header(
                    HttpHeaders.SET_COOKIE,
                    authCookieBuilder.buildAccessTokenCookie(result.tokens.accessToken).toString()
                )
                builder.header(
                    HttpHeaders.SET_COOKIE,
                    authCookieBuilder.buildRefreshTokenCookie(result.tokens.refreshToken).toString()
                )
                builder.body(
                    RefreshTokenResponse(
                        status = "SUCCESS",
                        expiresIn = result.tokens.accessTokenExpiry
                    )
                )
            }
            is RefreshResult.TokenReuse -> {
                val builder = ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                authCookieBuilder.buildClearCookies().forEach { cookie ->
                    builder.header(HttpHeaders.SET_COOKIE, cookie.toString())
                }
                builder.body(
                    ErrorResponse(
                        error = "TOKEN_REUSE_DETECTED",
                        message = "Security alert: Your session was invalidated due to suspicious activity."
                    )
                )
            }
            is RefreshResult.MissingToken,
            is RefreshResult.InvalidToken,
            is RefreshResult.SessionNotFound -> {
                val builder = ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                authCookieBuilder.buildClearCookies().forEach { cookie ->
                    builder.header(HttpHeaders.SET_COOKIE, cookie.toString())
                }
                builder.body(
                    ErrorResponse(
                        error = "TOKEN_EXPIRED",
                        message = "Session expired. Please sign in again."
                    )
                )
            }
        }
    }

    /**
     * Changes a user's password.
     *
     * This endpoint requires authentication via access_token cookie.
     * After a successful password change, all device trusts are revoked for security.
     *
     * @param request The password change request.
     * @param accessToken The access token from the cookie.
     * @param correlationId Optional correlation ID for distributed tracing.
     * @return 200 OK on success,
     *         401 Unauthorized if not authenticated or current password is wrong,
     *         400 Bad Request if new password is weak.
     */
    @PostMapping("/change-password")
    fun changePassword(
        @Valid @RequestBody request: ChangePasswordRequest,
        @CookieValue(value = "access_token", required = false) accessToken: String?,
        @RequestHeader("X-Correlation-ID", required = false) correlationId: String?
    ): ResponseEntity<Any> {
        // Authenticate user from access_token cookie
        if (accessToken.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ErrorResponse(
                    error = "UNAUTHORIZED",
                    message = "Authentication required"
                )
            )
        }

        val userId = tokenService.parseAccessToken(accessToken)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ErrorResponse(
                    error = "UNAUTHORIZED",
                    message = "Invalid or expired access token"
                )
            )

        val corrId = correlationId?.let { UUID.fromString(it) } ?: UUID.randomUUID()

        // Execute password change
        return when (val result = changePasswordUseCase.execute(
            userId = userId,
            currentPassword = request.currentPassword,
            newPassword = request.newPassword,
            correlationId = corrId
        )) {
            is ChangePasswordResult.Success -> {
                ResponseEntity.ok(ChangePasswordResponse())
            }
            is ChangePasswordResult.InvalidCurrentPassword -> {
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ErrorResponse(
                        error = "INVALID_PASSWORD",
                        message = result.message
                    )
                )
            }
            is ChangePasswordResult.WeakPassword -> {
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    ErrorResponse(
                        error = "WEAK_PASSWORD",
                        message = result.message
                    )
                )
            }
            is ChangePasswordResult.InternalError -> {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ErrorResponse(
                        error = "INTERNAL_ERROR",
                        message = result.message
                    )
                )
            }
        }
    }

    /**
     * Signs the current session out.
     *
     * If a valid access_token cookie is present, the corresponding session
     * is removed from Redis and a SessionInvalidated event is published.
     * Regardless of token validity, all auth cookies (access_token,
     * refresh_token, device_trust) are cleared with Max-Age=0 so a stale
     * or missing token still produces a clean signed-out state.
     *
     * @param accessToken Optional access token from cookie.
     * @return 200 OK with [LogoutResponse]; cookies are cleared in the response.
     */
    @PostMapping("/logout")
    fun logout(
        @CookieValue(value = "access_token", required = false) accessToken: String?
    ): ResponseEntity<LogoutResponse> {
        if (!accessToken.isNullOrBlank()) {
            tokenService.parseAccessTokenClaims(accessToken)?.let { claims ->
                val sessionId = claims.getStringClaim("sessionId")
                val subject = claims.subject
                if (!sessionId.isNullOrBlank() && !subject.isNullOrBlank()) {
                    try {
                        val userId = UUID.fromString(subject)
                        sessionService.invalidateSession(
                            sessionId = sessionId,
                            userId = userId,
                            reason = SessionInvalidated.REASON_LOGOUT
                        )
                    } catch (e: IllegalArgumentException) {
                        logger.warn("Logout: malformed userId in access token subject")
                    }
                }
            }
        }

        return buildClearCookieResponse(LogoutResponse())
    }

    /**
     * Signs the user out of every active session.
     *
     * Requires a valid access_token cookie. All sessions belonging to the
     * user are invalidated; each emits a SessionInvalidated event with
     * reason USER_LOGOUT_ALL. All auth cookies are cleared on the response.
     *
     * @param accessToken Required access token from cookie.
     * @return 200 OK with [LogoutAllResponse] including session count,
     *         401 Unauthorized if the token is missing or invalid.
     */
    @PostMapping("/logout/all")
    fun logoutAll(
        @CookieValue(value = "access_token", required = false) accessToken: String?
    ): ResponseEntity<Any> {
        if (accessToken.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ErrorResponse(
                    error = "UNAUTHORIZED",
                    message = "Authentication required"
                )
            )
        }

        val claims = tokenService.parseAccessTokenClaims(accessToken)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ErrorResponse(
                    error = "UNAUTHORIZED",
                    message = "Invalid or expired access token"
                )
            )

        val subject = claims.subject
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ErrorResponse(
                    error = "UNAUTHORIZED",
                    message = "Invalid access token"
                )
            )

        val userId = try {
            UUID.fromString(subject)
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ErrorResponse(
                    error = "UNAUTHORIZED",
                    message = "Invalid access token"
                )
            )
        }

        val sessions = sessionService.findUserSessions(userId)
        sessions.forEach { session ->
            sessionService.invalidateSession(
                sessionId = session.id,
                userId = userId,
                reason = SessionInvalidated.REASON_LOGOUT_ALL
            )
        }

        return buildClearCookieResponse(LogoutAllResponse(sessionsInvalidated = sessions.size))
    }

    /**
     * Builds a 200 OK response with all auth cookies cleared.
     */
    private fun <T : Any> buildClearCookieResponse(body: T): ResponseEntity<T> {
        val builder = ResponseEntity.ok()
        authCookieBuilder.buildClearCookies().forEach { cookie ->
            builder.header(HttpHeaders.SET_COOKIE, cookie.toString())
        }
        return builder.body(body)
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
                        message = inactiveMessageFor(error.status),
                        reason = error.status.name,
                        supportUrl = when (error.status) {
                            UserStatus.SUSPENDED, UserStatus.DEACTIVATED -> supportUrl
                            else -> null
                        },
                        supportEmail = when (error.status) {
                            UserStatus.SUSPENDED -> supportEmail
                            else -> null
                        },
                        deactivatedAt = error.deactivatedAt?.toString(),
                        reactivationAvailable = when (error.status) {
                            UserStatus.DEACTIVATED -> error.reactivationAvailable
                            else -> null
                        },
                        resendAvailableIn = when (error.status) {
                            UserStatus.PENDING_VERIFICATION -> error.resendAvailableIn
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
     * Human-readable message keyed off the inactive account status. Kept on
     * the controller so the message strings stay in the API layer rather
     * than the domain.
     */
    private fun inactiveMessageFor(status: UserStatus): String = when (status) {
        UserStatus.PENDING_VERIFICATION -> "Please verify your email address to continue."
        UserStatus.SUSPENDED -> "Your account has been suspended. Please contact support for assistance."
        UserStatus.DEACTIVATED -> "Your account has been deactivated. Would you like to reactivate it?"
        else -> "Account is not active"
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
