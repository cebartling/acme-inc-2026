package com.acme.identity.api.v1

import com.acme.identity.infrastructure.persistence.VerificationTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Test-only controller for acceptance and integration testing.
 *
 * This controller is only available when the "test" or "docker" profile is active.
 * It provides endpoints that expose internal data for testing purposes,
 * such as verification tokens.
 *
 * WARNING: This controller should NEVER be enabled in production environments.
 */
@RestController
@RequestMapping("/api/v1/test")
@Profile("test", "docker")
class TestController(
    private val verificationTokenRepository: VerificationTokenRepository
) {
    private val logger = LoggerFactory.getLogger(TestController::class.java)

    /**
     * Response DTO for verification token lookup.
     */
    data class VerificationTokenResponse(
        val token: String,
        val userId: String
    )

    /**
     * Retrieves the verification token for a user.
     *
     * This endpoint is intended for acceptance testing to allow tests to
     * retrieve the actual verification token sent to a user's email.
     *
     * @param userId The UUID of the user.
     * @return The verification token if found, or a 404 response.
     */
    @GetMapping("/users/{userId}/verification-token")
    fun getVerificationToken(@PathVariable userId: UUID): ResponseEntity<VerificationTokenResponse> {
        logger.debug("Test endpoint: Getting verification token for user {}", userId)

        val tokens = verificationTokenRepository.findByUserId(userId)
        val activeToken = tokens.firstOrNull { !it.isUsed() && !it.isExpired() }

        return if (activeToken != null) {
            logger.debug("Found active verification token for user {}", userId)
            ResponseEntity.ok(
                VerificationTokenResponse(
                    token = activeToken.token,
                    userId = userId.toString()
                )
            )
        } else {
            logger.debug("No active verification token found for user {}", userId)
            ResponseEntity.notFound().build()
        }
    }
}
