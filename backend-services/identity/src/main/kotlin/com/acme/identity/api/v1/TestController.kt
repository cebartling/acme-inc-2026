package com.acme.identity.api.v1

import com.acme.identity.domain.SmsRateLimit
import com.acme.identity.infrastructure.persistence.EventStoreRepository
import com.acme.identity.infrastructure.persistence.MfaChallengeRepository
import com.acme.identity.infrastructure.persistence.ResendRequestRepository
import com.acme.identity.infrastructure.persistence.SmsRateLimitRepository
import com.acme.identity.infrastructure.persistence.UsedTotpCodeRepository
import com.acme.identity.infrastructure.persistence.UserRepository
import com.acme.identity.infrastructure.persistence.VerificationTokenRepository
import com.acme.identity.infrastructure.sms.MockSmsProvider
import com.acme.identity.infrastructure.sms.SmsProvider
import com.acme.identity.infrastructure.util.PhoneNumberUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Test-only controller for acceptance and integration testing.
 *
 * This controller is only available when the "test" or "docker" profile is active.
 * It provides endpoints that expose internal data for testing purposes,
 * such as verification tokens and test session management.
 *
 * SECURITY: In addition to profile-based activation, all endpoints require
 * a valid X-Test-Api-Key header matching the configured test API key.
 * This provides defense-in-depth if the test profile is accidentally enabled.
 *
 * WARNING: This controller should NEVER be enabled in production environments.
 */
@RestController
@RequestMapping("/api/v1/test")
@Profile("test", "docker")
class TestController(
    private val verificationTokenRepository: VerificationTokenRepository,
    private val userRepository: UserRepository,
    private val mfaChallengeRepository: MfaChallengeRepository,
    private val usedTotpCodeRepository: UsedTotpCodeRepository,
    private val resendRequestRepository: ResendRequestRepository,
    private val smsRateLimitRepository: SmsRateLimitRepository,
    private val smsProvider: SmsProvider,
    private val sessionRepository: com.acme.identity.infrastructure.persistence.SessionRepository,
    private val sessionService: com.acme.identity.application.SessionService,
    private val eventStoreRepository: EventStoreRepository,
    private val jdbcTemplate: org.springframework.jdbc.core.JdbcTemplate,
    private val redisTemplate: org.springframework.data.redis.core.RedisTemplate<String, Any>,
    @Value("\${identity.test.api-key:test-api-key-for-acceptance-tests}")
    private val testApiKey: String
) {
    private val logger = LoggerFactory.getLogger(TestController::class.java)

    /**
     * Validates the test API key before processing any request.
     * This provides defense-in-depth beyond the @Profile annotation.
     *
     * @param apiKey The X-Test-Api-Key header value.
     * @throws ResponseStatusException if the API key is missing or invalid.
     */
    @ModelAttribute
    fun validateApiKey(@RequestHeader(value = "X-Test-Api-Key", required = false) apiKey: String?) {
        if (apiKey != testApiKey) {
            logger.warn("Invalid or missing test API key attempted")
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid or missing X-Test-Api-Key header")
        }
    }

    /**
     * In-memory storage for test sessions.
     * Each session tracks user IDs created during that session for cleanup.
     */
    private val testSessions = ConcurrentHashMap<String, TestSession>()

    /**
     * Data class representing a test session.
     */
    data class TestSession(
        val sessionId: String,
        val createdAt: Instant = Instant.now(),
        val userIds: MutableList<UUID> = mutableListOf(),
        val emails: MutableList<String> = mutableListOf()
    )

    // =========================================================================
    // Test Session Management
    // =========================================================================

    /**
     * Response DTO for session creation.
     */
    data class CreateSessionResponse(
        val sessionId: String,
        val createdAt: String
    )

    /**
     * Creates a new test session for tracking test data.
     *
     * @return The session ID and creation time.
     */
    @PostMapping("/sessions")
    fun createSession(): ResponseEntity<CreateSessionResponse> {
        val sessionId = "test-${UUID.randomUUID()}"
        val session = TestSession(sessionId = sessionId)
        testSessions[sessionId] = session

        logger.info("Created test session: {}", sessionId)
        return ResponseEntity.ok(
            CreateSessionResponse(
                sessionId = sessionId,
                createdAt = session.createdAt.toString()
            )
        )
    }

    /**
     * Request DTO for registering a user with a session.
     */
    data class RegisterUserRequest(
        val userId: String,
        val email: String
    )

    /**
     * Registers a user ID with a test session for cleanup.
     *
     * @param sessionId The session ID.
     * @param request The user registration request.
     * @return 200 OK if registered, 404 if session not found.
     */
    @PostMapping("/sessions/{sessionId}/users")
    fun registerUserWithSession(
        @PathVariable sessionId: String,
        @RequestBody request: RegisterUserRequest
    ): ResponseEntity<Void> {
        val session = testSessions[sessionId]
            ?: return ResponseEntity.notFound().build()

        val userId = UUID.fromString(request.userId)
        session.userIds.add(userId)
        session.emails.add(request.email.lowercase())

        logger.debug("Registered user {} with session {}", userId, sessionId)
        return ResponseEntity.ok().build()
    }

    /**
     * Response DTO for session cleanup.
     */
    data class CleanupSessionResponse(
        val sessionId: String,
        val deletedUsers: Int,
        val deletedEmails: Int
    )

    /**
     * Cleans up all data created during a test session (rollback equivalent).
     *
     * Deletes all users and related data registered with this session.
     * This provides transaction-like rollback semantics for tests.
     *
     * @param sessionId The session ID.
     * @return Cleanup statistics, or 404 if session not found.
     */
    @DeleteMapping("/sessions/{sessionId}")
    @Transactional
    fun cleanupSession(@PathVariable sessionId: String): ResponseEntity<CleanupSessionResponse> {
        val session = testSessions.remove(sessionId)
            ?: return ResponseEntity.notFound().build()

        logger.info("Cleaning up test session: {} ({} users, {} emails)",
            sessionId, session.userIds.size, session.emails.size)

        var deletedUsers = 0

        // Delete users in reverse order (LIFO) to handle any dependencies
        for (userId in session.userIds.reversed()) {
            try {
                deleteUserData(userId)
                deletedUsers++
            } catch (e: Exception) {
                logger.warn("Failed to delete user {}: {}", userId, e.message)
            }
        }

        // Clean up any resend requests by email
        for (email in session.emails) {
            try {
                resendRequestRepository.deleteByEmail(email)
            } catch (e: Exception) {
                logger.warn("Failed to delete resend requests for {}: {}", email, e.message)
            }
        }

        logger.info("Test session {} cleanup complete: {} users deleted", sessionId, deletedUsers)
        return ResponseEntity.ok(
            CleanupSessionResponse(
                sessionId = sessionId,
                deletedUsers = deletedUsers,
                deletedEmails = session.emails.size
            )
        )
    }

    /**
     * Helper to delete all data associated with a user.
     */
    private fun deleteUserData(userId: UUID) {
        // Delete in order of dependencies (children first)
        mfaChallengeRepository.deleteByUserId(userId)
        usedTotpCodeRepository.deleteByUserId(userId)
        smsRateLimitRepository.deleteByUserId(userId)
        verificationTokenRepository.deleteByUserId(userId)
        userRepository.deleteById(userId)
        logger.debug("Deleted user {} and all related data", userId)
    }

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
     * Performs comprehensive cleanup of all related data.
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
            // Delete all related data
            deleteUserData(user.id)
            // Also clean up resend requests
            resendRequestRepository.deleteByEmail(email.lowercase())
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
     * Response DTO for validation errors.
     */
    data class ValidationErrorResponse(
        val error: String,
        val message: String
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
    ): ResponseEntity<Any> {
        logger.debug("Test endpoint: Enabling MFA for user {}", userId)

        // Validate TOTP secret format
        val validationError = validateTotpSecret(request.totpSecret)
        if (validationError != null) {
            logger.warn("Invalid TOTP secret for user {}: {}", userId, validationError)
            return ResponseEntity.badRequest().body(
                ValidationErrorResponse(
                    error = "INVALID_TOTP_SECRET",
                    message = validationError
                )
            )
        }

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

    /**
     * Request DTO for enabling SMS MFA.
     */
    data class EnableSmsMfaRequest(
        val phoneNumber: String
    )

    /**
     * Response DTO for SMS MFA enablement.
     */
    data class EnableSmsMfaResponse(
        val userId: String,
        val mfaEnabled: Boolean,
        val smsMfaEnabled: Boolean,
        val maskedPhone: String
    )

    /**
     * Enables SMS MFA for a user.
     *
     * This endpoint is intended for acceptance testing to enable SMS MFA
     * for test users without going through the full phone verification flow.
     *
     * @param userId The UUID of the user.
     * @param request The request containing the phone number.
     * @return The MFA status if successful, or a 404 response.
     */
    @PostMapping("/users/{userId}/enable-sms-mfa")
    @Transactional
    fun enableSmsMfa(
        @PathVariable userId: UUID,
        @RequestBody request: EnableSmsMfaRequest
    ): ResponseEntity<Any> {
        logger.debug("Test endpoint: Enabling SMS MFA for user {}", userId)

        // Validate phone number format (basic E.164 validation)
        val validationError = validatePhoneNumber(request.phoneNumber)
        if (validationError != null) {
            logger.warn("Invalid phone number for user {}: {}", userId, validationError)
            return ResponseEntity.badRequest().body(
                ValidationErrorResponse(
                    error = "INVALID_PHONE_NUMBER",
                    message = validationError
                )
            )
        }

        val user = userRepository.findById(userId).orElse(null)

        return if (user != null) {
            user.mfaEnabled = true
            user.smsMfaEnabled = true
            user.phoneNumber = request.phoneNumber
            user.phoneVerified = true
            userRepository.save(user)

            logger.info("Enabled SMS MFA for user {} with phone {}", userId, PhoneNumberUtils.mask(request.phoneNumber))
            ResponseEntity.ok(
                EnableSmsMfaResponse(
                    userId = userId.toString(),
                    mfaEnabled = true,
                    smsMfaEnabled = true,
                    maskedPhone = PhoneNumberUtils.mask(request.phoneNumber)
                )
            )
        } else {
            logger.debug("No user found with id {}", userId)
            ResponseEntity.notFound().build()
        }
    }

    /**
     * Validates that a phone number is in E.164 format.
     *
     * @param phoneNumber The phone number to validate.
     * @return An error message if invalid, or null if valid.
     */
    private fun validatePhoneNumber(phoneNumber: String): String? {
        if (phoneNumber.isBlank()) {
            return "Phone number cannot be empty"
        }

        // E.164 format: + followed by country code and number (max 15 digits)
        val e164Regex = Regex("^\\+[1-9]\\d{1,14}$")
        if (!e164Regex.matches(phoneNumber)) {
            return "Phone number must be in E.164 format (e.g., +15551234567)"
        }

        return null
    }

    /**
     * Validates that a TOTP secret is properly formatted.
     *
     * @param secret The TOTP secret to validate.
     * @return An error message if invalid, or null if valid.
     */
    private fun validateTotpSecret(secret: String): String? {
        if (secret.isBlank()) {
            return "TOTP secret cannot be empty"
        }

        // Remove any padding characters for length check
        val secretWithoutPadding = secret.replace("=", "")

        // Base32 alphabet: A-Z and 2-7
        val base32Regex = Regex("^[A-Z2-7]+=*$")
        if (!base32Regex.matches(secret.uppercase())) {
            return "TOTP secret must be base32 encoded (A-Z, 2-7 characters only)"
        }

        // Minimum 26 base32 chars = 130 bits >= 128 bits (16 bytes) per RFC 4226
        if (secretWithoutPadding.length < MIN_TOTP_SECRET_LENGTH) {
            return "TOTP secret must be at least $MIN_TOTP_SECRET_LENGTH base32 characters (128 bits)"
        }

        // Maximum reasonable length check
        if (secret.length > MAX_TOTP_SECRET_LENGTH) {
            return "TOTP secret exceeds maximum length of $MAX_TOTP_SECRET_LENGTH characters"
        }

        return null
    }

    /**
     * Request DTO for expiring an MFA challenge.
     */
    data class ExpireMfaChallengeRequest(
        val mfaToken: String
    )

    /**
     * Response DTO for MFA challenge expiration.
     */
    data class ExpireMfaChallengeResponse(
        val mfaToken: String,
        val expired: Boolean
    )

    /**
     * Expires an MFA challenge immediately for testing.
     *
     * This endpoint is intended for acceptance testing to test the
     * challenge expiry behavior without waiting 5 minutes.
     *
     * @param request The request containing the MFA token.
     * @return The expiry status if successful, or a 404 response.
     */
    @PostMapping("/mfa/expire-challenge")
    @Transactional
    fun expireMfaChallenge(
        @RequestBody request: ExpireMfaChallengeRequest
    ): ResponseEntity<ExpireMfaChallengeResponse> {
        logger.debug("Test endpoint: Expiring MFA challenge with token {}", request.mfaToken.take(20))

        val updatedCount = mfaChallengeRepository.expireByToken(
            request.mfaToken,
            Instant.now().minusSeconds(1)
        )

        return if (updatedCount > 0) {
            logger.info("Expired MFA challenge for token {}", request.mfaToken.take(20))
            ResponseEntity.ok(
                ExpireMfaChallengeResponse(
                    mfaToken = request.mfaToken,
                    expired = true
                )
            )
        } else {
            logger.debug("No MFA challenge found with token {}", request.mfaToken.take(20))
            ResponseEntity.notFound().build()
        }
    }

    // =========================================================================
    // SMS Testing Endpoints
    // =========================================================================

    /**
     * Response DTO for getting the last SMS code.
     */
    data class LastSmsCodeResponse(
        val phoneNumber: String,
        val code: String?,
        val messageBody: String?
    )

    /**
     * Gets the last SMS verification code sent to a phone number.
     *
     * This endpoint is only available when using MockSmsProvider.
     * It allows acceptance tests to retrieve the actual SMS code
     * without hardcoding test values.
     *
     * @param phoneNumber The phone number (URL encoded, e.g., %2B15551234567).
     * @return The last SMS code if found, or a 404 response.
     */
    @GetMapping("/sms/last-code")
    fun getLastSmsCode(@org.springframework.web.bind.annotation.RequestParam phoneNumber: String): ResponseEntity<LastSmsCodeResponse> {
        logger.debug("Test endpoint: Getting last SMS code for {}", phoneNumber)

        if (smsProvider !is MockSmsProvider) {
            logger.warn("getLastSmsCode only works with MockSmsProvider, current provider: {}", smsProvider::class.simpleName)
            return ResponseEntity.badRequest().build()
        }

        val mockProvider = smsProvider as MockSmsProvider
        val code = mockProvider.extractCodeFromLastMessage(phoneNumber)
        val lastMessage = mockProvider.getLastMessageSentTo(phoneNumber)

        return if (code != null) {
            logger.debug("Found SMS code {} for phone {}", code, phoneNumber)
            ResponseEntity.ok(
                LastSmsCodeResponse(
                    phoneNumber = phoneNumber,
                    code = code,
                    messageBody = lastMessage?.body
                )
            )
        } else {
            logger.debug("No SMS code found for phone {}", phoneNumber)
            ResponseEntity.notFound().build()
        }
    }

    /**
     * Request DTO for adding SMS rate limit records.
     */
    data class AddSmsRateLimitRequest(
        val userId: String,
        val count: Int
    )

    /**
     * Response DTO for adding SMS rate limit records.
     */
    data class AddSmsRateLimitResponse(
        val userId: String,
        val recordsAdded: Int,
        val totalRecords: Long
    )

    /**
     * Adds SMS rate limit records for a user.
     *
     * This endpoint is intended for acceptance testing to simulate
     * a user who has already sent multiple SMS in the last hour,
     * allowing tests to verify rate limiting behavior.
     *
     * @param request The request containing userId and count.
     * @return The number of records added.
     */
    @PostMapping("/sms/add-rate-limit-records")
    @Transactional
    fun addSmsRateLimitRecords(
        @RequestBody request: AddSmsRateLimitRequest
    ): ResponseEntity<AddSmsRateLimitResponse> {
        logger.debug("Test endpoint: Adding {} SMS rate limit records for user {}", request.count, request.userId)

        val userId = UUID.fromString(request.userId)

        // Add rate limit records with timestamps spread over the last hour
        val now = Instant.now()
        repeat(request.count) { index ->
            val sentAt = now.minusSeconds((index * 10).toLong()) // Spread them out by 10 seconds each
            val record = SmsRateLimit(
                id = UUID.randomUUID(),
                userId = userId,
                sentAt = sentAt
            )
            smsRateLimitRepository.save(record)
        }

        val totalRecords = smsRateLimitRepository.countByUserIdSince(userId, now.minusSeconds(3600))

        logger.info("Added {} SMS rate limit records for user {}, total: {}", request.count, userId, totalRecords)
        return ResponseEntity.ok(
            AddSmsRateLimitResponse(
                userId = request.userId,
                recordsAdded = request.count,
                totalRecords = totalRecords
            )
        )
    }

    /**
     * Request DTO for resetting SMS cooldown.
     */
    data class ResetSmsCooldownRequest(
        val mfaToken: String
    )

    /**
     * Response DTO for resetting SMS cooldown.
     */
    data class ResetSmsCooldownResponse(
        val mfaToken: String,
        val cooldownReset: Boolean
    )

    /**
     * Resets the SMS resend cooldown for an MFA challenge.
     *
     * This endpoint is intended for acceptance testing to bypass
     * the 60-second resend cooldown, allowing tests to verify
     * resend functionality without waiting.
     *
     * Updates both the MFA challenge's lastSentAt and the most recent
     * SMS rate limit record's sentAt to ensure the cooldown check passes.
     *
     * @param request The request containing the MFA token.
     * @return The reset status.
     */
    @PostMapping("/sms/reset-cooldown")
    @Transactional
    fun resetSmsCooldown(
        @RequestBody request: ResetSmsCooldownRequest
    ): ResponseEntity<ResetSmsCooldownResponse> {
        logger.debug("Test endpoint: Resetting SMS cooldown for token {}", request.mfaToken.take(20))

        val twoMinutesAgo = Instant.now().minusSeconds(120) // Well past the 60-second cooldown

        // Update the MFA challenge's lastSentAt
        val challengeUpdated = mfaChallengeRepository.resetLastSentAt(
            request.mfaToken,
            twoMinutesAgo
        )

        if (challengeUpdated == 0) {
            logger.debug("No MFA challenge found with token {}", request.mfaToken.take(20))
            return ResponseEntity.notFound().build()
        }

        // Also update the most recent SMS rate limit record for the user
        // to ensure the SmsRateLimiter.checkResendCooldown() passes
        val challenge = mfaChallengeRepository.findByToken(request.mfaToken)
        if (challenge != null) {
            val rateLimitUpdated = smsRateLimitRepository.updateMostRecentSentAt(
                challenge.userId,
                twoMinutesAgo
            )
            logger.debug("Updated {} SMS rate limit records for user {}", rateLimitUpdated, challenge.userId)
        }

        logger.info("Reset SMS cooldown for token {}", request.mfaToken.take(20))
        return ResponseEntity.ok(
            ResetSmsCooldownResponse(
                mfaToken = request.mfaToken,
                cooldownReset = true
            )
        )
    }

    /**
     * Clears all mock SMS messages.
     *
     * This endpoint is intended for test cleanup between scenarios.
     */
    @DeleteMapping("/sms/clear-messages")
    fun clearSmsMessages(): ResponseEntity<Void> {
        logger.debug("Test endpoint: Clearing all mock SMS messages")

        if (smsProvider !is MockSmsProvider) {
            logger.warn("clearSmsMessages only works with MockSmsProvider")
            return ResponseEntity.badRequest().build()
        }

        (smsProvider as MockSmsProvider).clearAll()
        logger.info("Cleared all mock SMS messages")
        return ResponseEntity.noContent().build()
    }

    // =========================================================================
    // Session Management Testing Endpoints
    // =========================================================================

    /**
     * Response DTO for getting user sessions.
     */
    data class UserSessionsResponse(
        val userId: String,
        val sessions: List<SessionDto>
    )

    /**
     * DTO for session data.
     */
    data class SessionDto(
        val sessionId: String,
        val userId: String,
        val deviceId: String,
        val ipAddress: String,
        val userAgent: String,
        val tokenFamily: String,
        val createdAt: String,
        val expiresAt: String
    )

    /**
     * Gets all active sessions for a user from Redis.
     *
     * This endpoint is intended for acceptance testing to verify
     * session creation and concurrent session management.
     *
     * @param userId The UUID of the user.
     * @return The list of active sessions.
     */
    @GetMapping("/users/{userId}/sessions")
    fun getUserSessions(@PathVariable userId: UUID): ResponseEntity<UserSessionsResponse> {
        logger.debug("Test endpoint: Getting sessions for user {}", userId)

        val sessions = sessionRepository.findByUserId(userId)

        val sessionDtos = sessions.map { session ->
            SessionDto(
                sessionId = session.id,
                userId = session.userId.toString(),
                deviceId = session.deviceId,
                ipAddress = session.ipAddress,
                userAgent = session.userAgent,
                tokenFamily = session.tokenFamily,
                createdAt = session.createdAt.toString(),
                expiresAt = session.expiresAt.toString()
            )
        }

        logger.debug("Found {} sessions for user {}", sessionDtos.size, userId)
        return ResponseEntity.ok(
            UserSessionsResponse(
                userId = userId.toString(),
                sessions = sessionDtos
            )
        )
    }

    /**
     * Request DTO for creating test sessions.
     */
    data class CreateSessionsRequest(
        val count: Int,
        val differentDevices: Boolean = false
    )

    /**
     * Response DTO for creating test sessions.
     */
    data class CreateSessionsResponse(
        val userId: String,
        val sessionsCreated: Int,
        val totalSessions: Int
    )

    /**
     * Creates multiple test sessions for a user.
     *
     * This endpoint is intended for acceptance testing to simulate
     * users with multiple active sessions, for testing concurrent
     * session limit enforcement.
     *
     * @param userId The UUID of the user.
     * @param request The request containing count and device options.
     * @return The number of sessions created.
     */
    @PostMapping("/users/{userId}/create-sessions")
    @Transactional
    fun createTestSessions(
        @PathVariable userId: UUID,
        @RequestBody request: CreateSessionsRequest
    ): ResponseEntity<CreateSessionsResponse> {
        logger.debug("Test endpoint: Creating {} sessions for user {}", request.count, userId)

        repeat(request.count) { index ->
            val deviceId = if (request.differentDevices) {
                "test-device-${UUID.randomUUID()}"
            } else {
                "test-device-single"
            }

            val tokenFamily = "fam_test_${UUID.randomUUID()}"

            sessionService.createSession(
                userId = userId,
                deviceId = deviceId,
                ipAddress = "192.168.1.$index",
                userAgent = "Test-Agent/$index",
                tokenFamily = tokenFamily
            )
        }

        val totalSessions = sessionRepository.countByUserId(userId).toInt()

        logger.info("Created {} test sessions for user {}, total: {}", request.count, userId, totalSessions)
        return ResponseEntity.ok(
            CreateSessionsResponse(
                userId = userId.toString(),
                sessionsCreated = request.count,
                totalSessions = totalSessions
            )
        )
    }

    /**
     * Response DTO for getting session TTL.
     */
    data class SessionTtlResponse(
        val sessionId: String,
        val ttl: Long
    )

    /**
     * Gets the TTL (time to live) for a session in Redis.
     *
     * This endpoint is intended for acceptance testing to verify
     * that sessions have the correct expiration time set.
     *
     * @param sessionId The session ID.
     * @return The TTL in seconds, or -1 if no TTL is set, or -2 if key doesn't exist.
     */
    @GetMapping("/sessions/{sessionId}/ttl")
    fun getSessionTtl(@PathVariable sessionId: String): ResponseEntity<SessionTtlResponse> {
        logger.debug("Test endpoint: Getting TTL for session {}", sessionId)

        val key = "sessions:$sessionId"
        val ttl = redisTemplate.getExpire(key, java.util.concurrent.TimeUnit.SECONDS)

        logger.debug("Session {} TTL: {} seconds", sessionId, ttl)
        return ResponseEntity.ok(
            SessionTtlResponse(
                sessionId = sessionId,
                ttl = ttl ?: -2
            )
        )
    }

    // =========================================================================
    // Event Store Testing Endpoints
    // =========================================================================

    /**
     * Response DTO for querying events.
     */
    data class EventsResponse(
        val eventType: String,
        val events: List<Map<String, Any?>>
    )

    /**
     * Queries events of a specific type from the event store.
     *
     * This endpoint is intended for acceptance testing to verify
     * that domain events are being published correctly.
     *
     * @param eventType The type of event to query (SessionCreated, UserLoggedIn, etc.).
     * @param userId Optional user ID filter.
     * @param limit Maximum number of events to return (default: 100).
     * @return The list of matching events.
     */
    @GetMapping("/events/{eventType}")
    fun getEvents(
        @PathVariable eventType: String,
        @org.springframework.web.bind.annotation.RequestParam(required = false) userId: UUID?,
        @org.springframework.web.bind.annotation.RequestParam(defaultValue = "100") limit: Int
    ): ResponseEntity<EventsResponse> {
        logger.debug("Test endpoint: Querying events of type {} for user {}", eventType, userId)

        val sql = if (userId != null) {
            """
            SELECT event_id, event_type, event_version, timestamp,
                   aggregate_id, aggregate_type, correlation_id, payload
            FROM event_store
            WHERE event_type = ? AND aggregate_id = ?
            ORDER BY timestamp DESC
            LIMIT ?
            """.trimIndent()
        } else {
            """
            SELECT event_id, event_type, event_version, timestamp,
                   aggregate_id, aggregate_type, correlation_id, payload
            FROM event_store
            WHERE event_type = ?
            ORDER BY timestamp DESC
            LIMIT ?
            """.trimIndent()
        }

        val events = if (userId != null) {
            jdbcTemplate.queryForList(sql, eventType, userId, limit)
        } else {
            jdbcTemplate.queryForList(sql, eventType, limit)
        }

        logger.debug("Found {} events of type {}", events.size, eventType)
        return ResponseEntity.ok(
            EventsResponse(
                eventType = eventType,
                events = events
            )
        )
    }

    // =========================================================================
    // Log Testing Endpoints
    // =========================================================================

    /**
     * Response DTO for getting recent logs.
     */
    data class RecentLogsResponse(
        val logs: String,
        val lineCount: Int
    )

    /**
     * Gets recent application logs.
     *
     * This endpoint is intended for acceptance testing to verify
     * that tokens and other sensitive data are not being logged.
     *
     * Note: This is a simplified implementation that returns an empty
     * string since direct log access would require additional configuration.
     * In a real implementation, this would integrate with your logging
     * infrastructure (e.g., query from Loki, read from file, etc.).
     *
     * @param lines Number of log lines to return (default: 100).
     * @return Recent log lines.
     */
    @GetMapping("/logs/recent")
    fun getRecentLogs(
        @org.springframework.web.bind.annotation.RequestParam(defaultValue = "100") lines: Int
    ): ResponseEntity<RecentLogsResponse> {
        logger.debug("Test endpoint: Getting recent {} log lines", lines)

        // Simplified implementation - returns empty for now
        // In production test environment, this would query from your
        // logging infrastructure (Loki, file system, etc.)
        val logs = ""

        return ResponseEntity.ok(
            RecentLogsResponse(
                logs = logs,
                lineCount = 0
            )
        )
    }

    /**
     * Response DTO for getting SMS code by user ID.
     */
    data class UserSmsCodeResponse(
        val userId: String,
        val phoneNumber: String?,
        val code: String?,
        val messageBody: String?
    )

    /**
     * Gets the last SMS verification code sent to a user.
     *
     * This endpoint is only available when using MockSmsProvider.
     * It allows acceptance tests to retrieve the SMS code by user ID
     * rather than requiring the phone number.
     *
     * @param userId The UUID of the user.
     * @return The last SMS code if found, or a 404 response.
     */
    @GetMapping("/users/{userId}/sms-code")
    fun getUserSmsCode(@PathVariable userId: UUID): ResponseEntity<UserSmsCodeResponse> {
        logger.debug("Test endpoint: Getting SMS code for user {}", userId)

        if (smsProvider !is MockSmsProvider) {
            logger.warn("getUserSmsCode only works with MockSmsProvider")
            return ResponseEntity.badRequest().build()
        }

        val user = userRepository.findById(userId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val phoneNumber = user.phoneNumber
            ?: return ResponseEntity.notFound().build()

        val mockProvider = smsProvider as MockSmsProvider
        val code = mockProvider.extractCodeFromLastMessage(phoneNumber)
        val lastMessage = mockProvider.getLastMessageSentTo(phoneNumber)

        return if (code != null) {
            logger.debug("Found SMS code {} for user {}", code, userId)
            ResponseEntity.ok(
                UserSmsCodeResponse(
                    userId = userId.toString(),
                    phoneNumber = phoneNumber,
                    code = code,
                    messageBody = lastMessage?.body
                )
            )
        } else {
            logger.debug("No SMS code found for user {}", userId)
            ResponseEntity.notFound().build()
        }
    }

    companion object {
        // Minimum 26 base32 chars = 130 bits >= 128 bits (16 bytes) per RFC 4226
        private const val MIN_TOTP_SECRET_LENGTH = 26
        // Maximum reasonable length for a TOTP secret
        private const val MAX_TOTP_SECRET_LENGTH = 128
    }
}
