package com.acme.identity.api.v1

import com.acme.identity.api.v1.dto.MfaVerifyErrorResponse
import com.acme.identity.api.v1.dto.MfaVerifyRequest
import com.acme.identity.api.v1.dto.MfaVerifyResponse
import com.acme.identity.application.MfaVerificationContext
import com.acme.identity.application.MfaVerificationError
import com.acme.identity.application.MfaVerificationRequest
import com.acme.identity.application.VerifyMfaUseCase
import com.acme.identity.domain.MfaMethod
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
 * @property verifyMfaUseCase The use case for MFA verification.
 */
@RestController
@RequestMapping("/api/v1/auth/mfa")
class MfaController(
    private val verifyMfaUseCase: VerifyMfaUseCase
) {
    private val logger = LoggerFactory.getLogger(MfaController::class.java)

    /**
     * Verifies an MFA code for a pending authentication challenge.
     *
     * This endpoint validates a TOTP code against an active MFA challenge.
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
                    message = "Invalid MFA method. Supported methods: TOTP"
                )
            )
        }

        val verificationRequest = MfaVerificationRequest(
            mfaToken = request.mfaToken,
            code = request.code,
            method = mfaMethod,
            rememberDevice = request.rememberDevice
        )

        return verifyMfaUseCase.execute(verificationRequest, context).fold(
            ifLeft = { error ->
                mapErrorToResponse(error)
            },
            ifRight = { result ->
                ResponseEntity.ok(
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
     * Maps an MFA verification error to the appropriate HTTP response.
     */
    private fun mapErrorToResponse(error: MfaVerificationError): ResponseEntity<Any> {
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
