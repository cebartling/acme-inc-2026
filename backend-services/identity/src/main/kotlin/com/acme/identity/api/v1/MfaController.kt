package com.acme.identity.api.v1

import com.acme.identity.api.v1.dto.MfaResendErrorResponse
import com.acme.identity.api.v1.dto.MfaResendRequest
import com.acme.identity.api.v1.dto.MfaResendResponse
import com.acme.identity.api.v1.dto.MfaVerifyErrorResponse
import com.acme.identity.api.v1.dto.MfaVerifyRequest
import com.acme.identity.api.v1.dto.MfaVerifyResponse
import com.acme.identity.application.*
import com.acme.identity.domain.DeviceInfo
import com.acme.identity.domain.MfaMethod
import com.acme.identity.domain.events.UserLoggedIn
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.EventStoreRepository
import com.acme.identity.infrastructure.persistence.UserRepository
import com.acme.identity.infrastructure.security.AuthCookieBuilder
import jakarta.servlet.http.HttpServletRequest
import java.net.InetAddress
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * REST controller for MFA (Multi-Factor Authentication) endpoints.
 *
 * Provides the public API for MFA verification operations.
 * All endpoints are versioned under `/api/v1/auth/mfa`.
 *
 * @property verifyMfaUseCase The use case for TOTP MFA verification.
 * @property smsMfaService The service for SMS MFA operations.
 */
@RestController
@RequestMapping("/api/v1/auth/mfa")
class MfaController(
    private val verifyMfaUseCase: VerifyMfaUseCase,
    private val smsMfaService: SmsMfaService,
    private val tokenService: TokenService,
    private val sessionService: SessionService,
    private val authCookieBuilder: AuthCookieBuilder,
    private val userRepository: UserRepository,
    private val eventStoreRepository: EventStoreRepository,
    private val userEventPublisher: UserEventPublisher
) {
    private val logger = LoggerFactory.getLogger(MfaController::class.java)

    /**
     * Verifies an MFA code for a pending authentication challenge.
     *
     * This endpoint validates a TOTP or SMS code against an active MFA challenge.
     * On success, the user is fully authenticated.
     * On failure, an error response indicates the reason and remaining attempts.
     *
     * @param request The MFA verification request containing the token and code.
     * @param httpRequest The HTTP servlet request (for IP extraction).
     * @param correlationId Optional correlation ID for distributed tracing.
     * @return 200 OK with verification response on success,
     *         401 Unauthorized for invalid code or expired challenge.
     */
    @PostMapping("/verify")
    fun verify(
        @Valid @RequestBody request: MfaVerifyRequest,
        httpRequest: HttpServletRequest,
        @RequestHeader("X-Correlation-ID", required = false) correlationId: String?
    ): ResponseEntity<Any> {
        val clientIp = getClientIp(httpRequest)
        val userAgent = httpRequest.getHeader("User-Agent") ?: "unknown"

        val context = MfaVerificationContext(
            ipAddress = clientIp,
            userAgent = userAgent,
            correlationId = correlationId?.let { UUID.fromString(it) } ?: UUID.randomUUID()
        )

        val mfaMethod = try {
            MfaMethod.valueOf(request.method.uppercase())
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid MFA method: {}", request.method)
            return ResponseEntity.badRequest().body(
                MfaVerifyErrorResponse(
                    error = "INVALID_METHOD",
                    message = "Invalid MFA method. Supported methods: TOTP, SMS"
                )
            )
        }

        // Route to appropriate verification service based on method
        return when (mfaMethod) {
            MfaMethod.SMS -> verifySms(request, context)
            MfaMethod.TOTP -> verifyTotp(request, mfaMethod, context)
            MfaMethod.EMAIL -> ResponseEntity.badRequest().body(
                MfaVerifyErrorResponse(
                    error = "UNSUPPORTED_METHOD",
                    message = "Email MFA is not yet supported"
                )
            )
        }
    }

    /**
     * Verifies a TOTP code.
     */
    private fun verifyTotp(
        request: MfaVerifyRequest,
        method: MfaMethod,
        context: MfaVerificationContext
    ): ResponseEntity<Any> {
        val verificationRequest = MfaVerificationRequest(
            mfaToken = request.mfaToken,
            code = request.code,
            method = method,
            rememberDevice = request.rememberDevice
        )

        return verifyMfaUseCase.execute(verificationRequest, context).fold(
            ifLeft = { error ->
                mapTotpErrorToResponse(error)
            },
            ifRight = { result ->
                // Generate tokens and create session
                val responseWithCookies = createSessionAndGenerateTokens(
                    userId = result.userId,
                    ipAddress = context.ipAddress,
                    userAgent = context.userAgent,
                    deviceFingerprint = request.deviceFingerprint,
                    mfaMethod = "TOTP",
                    correlationId = context.correlationId
                )

                // Return response with cookies set
                responseWithCookies.body(
                    MfaVerifyResponse(
                        userId = result.userId,
                        email = result.email,
                        firstName = result.firstName,
                        lastName = result.lastName,
                        deviceTrusted = result.deviceRemembered
                    )
                )
            }
        )
    }

    /**
     * Verifies an SMS code.
     */
    private fun verifySms(
        request: MfaVerifyRequest,
        context: MfaVerificationContext
    ): ResponseEntity<Any> {
        return smsMfaService.verifyCode(
            mfaToken = request.mfaToken,
            code = request.code,
            rememberDevice = request.rememberDevice,
            context = context
        ).fold(
            ifLeft = { error ->
                mapSmsErrorToResponse(error)
            },
            ifRight = { result ->
                // Generate tokens and create session
                val responseWithCookies = createSessionAndGenerateTokens(
                    userId = result.userId,
                    ipAddress = context.ipAddress,
                    userAgent = context.userAgent,
                    deviceFingerprint = request.deviceFingerprint,
                    mfaMethod = "SMS",
                    correlationId = context.correlationId
                )

                // Return response with cookies set
                responseWithCookies.body(
                    MfaVerifyResponse(
                        userId = result.userId,
                        email = result.email,
                        firstName = result.firstName,
                        lastName = result.lastName,
                        deviceTrusted = result.deviceRemembered
                    )
                )
            }
        )
    }

    /**
     * Resends an SMS verification code.
     *
     * This endpoint generates a new code and sends it via SMS.
     * Subject to rate limiting (max 3 SMS per hour) and cooldown (60 seconds between resends).
     *
     * @param request The resend request containing the MFA token.
     * @param correlationId Optional correlation ID for distributed tracing.
     * @return 200 OK with resend result on success,
     *         429 Too Many Requests if rate limited or cooldown active.
     */
    @PostMapping("/resend")
    fun resend(
        @Valid @RequestBody request: MfaResendRequest,
        @RequestHeader("X-Correlation-ID", required = false) correlationId: String?
    ): ResponseEntity<Any> {
        val correlationUuid = correlationId?.let { UUID.fromString(it) } ?: UUID.randomUUID()

        if (request.method.uppercase() != "SMS") {
            return ResponseEntity.badRequest().body(
                MfaResendErrorResponse(
                    error = "INVALID_METHOD",
                    message = "Resend is only supported for SMS method"
                )
            )
        }

        return smsMfaService.resendCode(request.mfaToken, correlationUuid).fold(
            ifLeft = { error ->
                mapSmsResendErrorToResponse(error)
            },
            ifRight = { result ->
                ResponseEntity.ok(
                    MfaResendResponse(
                        maskedPhone = result.maskedPhone,
                        expiresIn = result.expiresIn,
                        resendAvailableIn = result.resendAvailableIn
                    )
                )
            }
        )
    }

    /**
     * Maps a TOTP MFA verification error to the appropriate HTTP response.
     */
    private fun mapTotpErrorToResponse(error: MfaVerificationError): ResponseEntity<Any> {
        return when (error) {
            is MfaVerificationError.InvalidToken -> {
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    MfaVerifyErrorResponse(
                        error = "INVALID_MFA_TOKEN",
                        message = "Invalid or expired MFA token. Please sign in again."
                    )
                )
            }
            is MfaVerificationError.Expired -> {
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    MfaVerifyErrorResponse(
                        error = "MFA_EXPIRED",
                        message = "MFA challenge has expired. Please sign in again."
                    )
                )
            }
            is MfaVerificationError.InvalidCode -> {
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    MfaVerifyErrorResponse(
                        error = "INVALID_MFA_CODE",
                        message = "Invalid verification code",
                        remainingAttempts = error.remainingAttempts
                    )
                )
            }
            is MfaVerificationError.CodeAlreadyUsed -> {
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    MfaVerifyErrorResponse(
                        error = "INVALID_MFA_CODE",
                        message = "This code has already been used. Please wait for a new code.",
                        remainingAttempts = error.remainingAttempts
                    )
                )
            }
        }
    }

    /**
     * Maps an SMS MFA verification error to the appropriate HTTP response.
     */
    private fun mapSmsErrorToResponse(error: SmsMfaError): ResponseEntity<Any> {
        return when (error) {
            is SmsMfaError.InvalidToken -> {
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    MfaVerifyErrorResponse(
                        error = "INVALID_MFA_TOKEN",
                        message = "Invalid or expired MFA token. Please sign in again."
                    )
                )
            }
            is SmsMfaError.Expired -> {
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    MfaVerifyErrorResponse(
                        error = "MFA_EXPIRED",
                        message = "MFA challenge has expired. Please sign in again."
                    )
                )
            }
            is SmsMfaError.InvalidCode -> {
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    MfaVerifyErrorResponse(
                        error = "INVALID_MFA_CODE",
                        message = "Invalid verification code",
                        remainingAttempts = error.remainingAttempts
                    )
                )
            }
            is SmsMfaError.SmsNotConfigured, is SmsMfaError.PhoneNotVerified -> {
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    MfaVerifyErrorResponse(
                        error = "SMS_NOT_CONFIGURED",
                        message = "SMS MFA is not configured for this account."
                    )
                )
            }
            is SmsMfaError.RateLimited -> {
                ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                    MfaVerifyErrorResponse(
                        error = "SMS_RATE_LIMITED",
                        message = "Too many SMS requests. Please try again later."
                    )
                )
            }
            is SmsMfaError.CooldownActive -> {
                ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                    MfaVerifyErrorResponse(
                        error = "COOLDOWN_ACTIVE",
                        message = "Please wait before requesting another code."
                    )
                )
            }
            is SmsMfaError.SendFailed -> {
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                    MfaVerifyErrorResponse(
                        error = "SMS_SEND_FAILED",
                        message = "Failed to send SMS. Please try again."
                    )
                )
            }
        }
    }

    /**
     * Maps an SMS resend error to the appropriate HTTP response.
     */
    private fun mapSmsResendErrorToResponse(error: SmsMfaError): ResponseEntity<Any> {
        return when (error) {
            is SmsMfaError.InvalidToken -> {
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    MfaResendErrorResponse(
                        error = "INVALID_MFA_TOKEN",
                        message = "Invalid or expired MFA token. Please sign in again."
                    )
                )
            }
            is SmsMfaError.Expired -> {
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    MfaResendErrorResponse(
                        error = "MFA_EXPIRED",
                        message = "MFA challenge has expired. Please sign in again."
                    )
                )
            }
            is SmsMfaError.RateLimited -> {
                ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                    MfaResendErrorResponse(
                        error = "SMS_RATE_LIMITED",
                        message = "Too many SMS requests. Please try again later.",
                        retryAfter = error.retryAfterSeconds
                    )
                )
            }
            is SmsMfaError.CooldownActive -> {
                ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                    MfaResendErrorResponse(
                        error = "COOLDOWN_ACTIVE",
                        message = "Please wait before requesting another code.",
                        resendAvailableIn = error.waitSeconds
                    )
                )
            }
            is SmsMfaError.SendFailed -> {
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                    MfaResendErrorResponse(
                        error = "SMS_SEND_FAILED",
                        message = "Failed to send SMS. Please try again."
                    )
                )
            }
            is SmsMfaError.SmsNotConfigured, is SmsMfaError.PhoneNotVerified -> {
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    MfaResendErrorResponse(
                        error = "SMS_NOT_CONFIGURED",
                        message = "SMS MFA is not configured for this account."
                    )
                )
            }
            is SmsMfaError.InvalidCode -> {
                // This shouldn't happen for resend, but handle it
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    MfaResendErrorResponse(
                        error = "INVALID_REQUEST",
                        message = "Invalid resend request."
                    )
                )
            }
        }
    }

    /**
     * Creates a session and generates JWT tokens after successful MFA verification.
     *
     * This method:
     * 1. Fetches the user from the database
     * 2. Generates a token family for refresh token rotation
     * 3. Creates a session in Redis
     * 4. Generates access and refresh tokens
     * 5. Sets secure HttpOnly cookies
     * 6. Publishes UserLoggedIn event
     *
     * @param userId The authenticated user's ID.
     * @param ipAddress The client's IP address.
     * @param userAgent The client's User-Agent.
     * @param deviceFingerprint Optional device fingerprint.
     * @param mfaMethod The MFA method used (TOTP, SMS).
     * @param correlationId The correlation ID for tracing.
     * @return ResponseEntity with Set-Cookie headers.
     */
    private fun createSessionAndGenerateTokens(
        userId: UUID,
        ipAddress: String,
        userAgent: String,
        deviceFingerprint: String?,
        mfaMethod: String,
        correlationId: UUID
    ): ResponseEntity.BodyBuilder {
        // Get the user
        val user = userRepository.findById(userId).orElseThrow {
            IllegalStateException("User not found after successful MFA verification: $userId")
        }

        // Create device info
        val deviceInfo = DeviceInfo.fromRequest(
            ipAddress = ipAddress,
            userAgent = userAgent,
            fingerprint = deviceFingerprint
        )

        // Generate token family for refresh token rotation
        val tokenFamily = "fam_${UUID.randomUUID()}"

        // Create session
        val session = sessionService.createSession(
            userId = userId,
            deviceId = deviceInfo.deviceId,
            ipAddress = ipAddress,
            userAgent = userAgent,
            tokenFamily = tokenFamily
        )

        // Generate tokens
        val tokens = tokenService.createTokens(
            user = user,
            sessionId = session.id,
            tokenFamily = tokenFamily
        )

        // Build cookies
        val accessTokenCookie = authCookieBuilder.buildAccessTokenCookie(tokens.accessToken)
        val refreshTokenCookie = authCookieBuilder.buildRefreshTokenCookie(tokens.refreshToken)

        // Publish UserLoggedIn event
        val event = UserLoggedIn.create(
            userId = userId,
            sessionId = session.id,
            ipAddress = ipAddress,
            userAgent = userAgent,
            deviceFingerprint = deviceFingerprint,
            mfaUsed = true,
            mfaMethod = mfaMethod,
            loginSource = "WEB",
            correlationId = correlationId
        )
        eventStoreRepository.append(event)
        userEventPublisher.publish(event)

        logger.info("Created session {} and generated tokens for user {}", session.id, userId)

        // Return response builder with cookies
        return ResponseEntity.ok()
            .header("Set-Cookie", accessTokenCookie.toString())
            .header("Set-Cookie", refreshTokenCookie.toString())
    }

    /**
     * Extracts the client IP address from the request.
     *
     * Handles X-Forwarded-For header for clients behind proxies/load balancers.
     * Validates that the extracted value is a valid IP address format.
     */
    private fun getClientIp(request: HttpServletRequest): String {
        val xForwardedFor = request.getHeader("X-Forwarded-For")

        if (xForwardedFor.isNullOrBlank() || xForwardedFor.length > MAX_FORWARDED_HEADER_LENGTH) {
            return request.remoteAddr
        }

        val candidateIp = xForwardedFor
            .split(",")
            .take(MAX_FORWARDED_ENTRIES)
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && isValidIpAddress(it) }

        return candidateIp ?: request.remoteAddr
    }

    /**
     * Validates that a string is a valid IPv4 or IPv6 address.
     */
    private fun isValidIpAddress(ip: String): Boolean {
        if (ip.length > MAX_IP_LENGTH) {
            return false
        }

        return try {
            InetAddress.getByName(ip)
            true
        } catch (e: Exception) {
            logger.debug("Invalid IP address format in X-Forwarded-For: {}", ip)
            false
        }
    }

    companion object {
        private const val MAX_FORWARDED_HEADER_LENGTH = 500
        private const val MAX_FORWARDED_ENTRIES = 10
        private const val MAX_IP_LENGTH = 45 // Max length of IPv6 address
    }
}
