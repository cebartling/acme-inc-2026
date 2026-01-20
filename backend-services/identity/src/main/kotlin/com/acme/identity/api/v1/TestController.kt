package com.acme.identity.api.v1

import com.acme.identity.infrastructure.persistence.UserRepository
import com.acme.identity.infrastructure.persistence.VerificationTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
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
    private val verificationTokenRepository: VerificationTokenRepository,
    private val userRepository: UserRepository
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

    /**
     * Deletes a user by email address.
     *
     * This endpoint is intended for acceptance testing to allow tests to
     * clean up users before recreating them with specific credentials.
     *
     * @param email The email address of the user to delete.
     * @return 204 No Content on success, 404 if user not found.
     */
    @DeleteMapping("/users/by-email/{email}")
    @Transactional
    fun deleteUserByEmail(@PathVariable email: String): ResponseEntity<Void> {
        logger.debug("Test endpoint: Deleting user by email {}", email)

        val user = userRepository.findByEmail(email.lowercase())

        return if (user != null) {
            // Delete verification tokens first (foreign key constraint)
            verificationTokenRepository.deleteByUserId(user.id)
            // Delete the user
            userRepository.delete(user)
            logger.info("Deleted user {} with email {}", user.id, email)
            ResponseEntity.noContent().build()
        } else {
            logger.debug("No user found with email {}", email)
            ResponseEntity.notFound().build()
        }
    }

    /**
     * Request DTO for enabling MFA.
     */
    data class EnableMfaRequest(
        val totpSecret: String
    )

    /**
     * Response DTO for MFA enablement.
     */
    data class EnableMfaResponse(
        val userId: String,
        val mfaEnabled: Boolean,
        val totpEnabled: Boolean
    )

    /**
     * Enables TOTP MFA for a user.
     *
     * This endpoint is intended for acceptance testing to enable MFA
     * for test users without going through the full MFA setup flow.
     *
     * @param userId The UUID of the user.
     * @param request The request containing the TOTP secret.
     * @return The MFA status if successful, or a 404 response.
     */
    @PostMapping("/users/{userId}/enable-mfa")
    @Transactional
    fun enableMfa(
        @PathVariable userId: UUID,
        @RequestBody request: EnableMfaRequest
    ): ResponseEntity<EnableMfaResponse> {
        logger.debug("Test endpoint: Enabling MFA for user {}", userId)

        val user = userRepository.findById(userId).orElse(null)

        return if (user != null) {
            user.mfaEnabled = true
            user.totpEnabled = true
            user.totpSecret = request.totpSecret
            userRepository.save(user)

            logger.info("Enabled MFA for user {}", userId)
            ResponseEntity.ok(
                EnableMfaResponse(
                    userId = userId.toString(),
                    mfaEnabled = true,
                    totpEnabled = true
                )
            )
        } else {
            logger.debug("No user found with id {}", userId)
            ResponseEntity.notFound().build()
        }
    }
}
