package com.acme.identity.api.v1

import com.acme.identity.api.v1.dto.ErrorResponse
import com.acme.identity.api.v1.dto.RegisterUserRequest
import com.acme.identity.api.v1.dto.RegisterUserResponse
import com.acme.identity.application.RegisterUserResult
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

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val registerUserUseCase: RegisterUserUseCase,
    private val rateLimiter: RateLimiter
) {
    private val logger = LoggerFactory.getLogger(UserController::class.java)

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

        return when (val result = registerUserUseCase.execute(request, registrationSource, corrId)) {
            is RegisterUserResult.Success -> {
                ResponseEntity.status(HttpStatus.CREATED).body(result.response)
            }

            is RegisterUserResult.DuplicateEmail -> {
                ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ErrorResponse(
                        error = "DUPLICATE_EMAIL",
                        message = result.message
                    )
                )
            }

            is RegisterUserResult.Error -> {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ErrorResponse(
                        error = "REGISTRATION_ERROR",
                        message = result.message
                    )
                )
            }
        }
    }

    private fun getClientIp(request: HttpServletRequest): String {
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        return if (!xForwardedFor.isNullOrBlank()) {
            xForwardedFor.split(",").first().trim()
        } else {
            request.remoteAddr
        }
    }

    private fun parseRegistrationSource(source: String?): RegistrationSource {
        return when (source?.uppercase()) {
            "MOBILE" -> RegistrationSource.MOBILE
            "API" -> RegistrationSource.API
            else -> RegistrationSource.WEB
        }
    }
}
