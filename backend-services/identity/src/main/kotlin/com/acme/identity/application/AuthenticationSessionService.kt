package com.acme.identity.application

import com.acme.identity.domain.DeviceInfo
import com.acme.identity.domain.events.UserLoggedIn
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.EventStoreRepository
import com.acme.identity.infrastructure.persistence.UserRepository
import com.acme.identity.infrastructure.security.AuthCookieBuilder
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Service for creating authenticated sessions with tokens and cookies.
 *
 * Encapsulates the complete flow of:
 * 1. Creating a session in Redis
 * 2. Generating JWT access and refresh tokens
 * 3. Building HTTP-only secure cookies
 * 4. Optionally creating device trust for MFA bypass
 * 5. Publishing UserLoggedIn events
 *
 * This service is used by both AuthenticationController (for direct auth success)
 * and MfaController (for post-MFA auth success) to ensure consistent behavior.
 *
 * @property userRepository Repository for user data access.
 * @property sessionService Service for managing user sessions.
 * @property tokenService Service for generating JWT tokens.
 * @property deviceTrustService Service for managing device trust.
 * @property authCookieBuilder Builder for creating authentication cookies.
 * @property eventStoreRepository Repository for storing domain events.
 * @property userEventPublisher Publisher for user-related events.
 */
@Service
class AuthenticationSessionService(
    private val userRepository: UserRepository,
    private val sessionService: SessionService,
    private val tokenService: TokenService,
    private val deviceTrustService: DeviceTrustService,
    private val authCookieBuilder: AuthCookieBuilder,
    private val eventStoreRepository: EventStoreRepository,
    private val userEventPublisher: UserEventPublisher
) {
    private val logger = LoggerFactory.getLogger(AuthenticationSessionService::class.java)

    /**
     * Creates a complete authenticated session with tokens and cookies.
     *
     * This method performs all the steps needed to establish a fully authenticated session:
     * - Fetches the user from the database
     * - Creates a session in Redis
     * - Generates JWT access and refresh tokens
     * - Builds secure HTTP-only cookies
     * - Optionally creates device trust if rememberDevice is true and deviceFingerprint is provided
     * - Publishes a UserLoggedIn event
     *
     * @param userId The authenticated user's ID.
     * @param ipAddress The client's IP address.
     * @param userAgent The client's User-Agent.
     * @param deviceFingerprint Optional device fingerprint for device trust.
     * @param rememberDevice Whether to create a device trust for future MFA bypass.
     * @param mfaUsed Whether MFA was used for this authentication.
     * @param mfaMethod The MFA method used (TOTP, SMS) or null if no MFA was used.
     * @param correlationId The correlation ID for distributed tracing.
     * @return ResponseEntity.BodyBuilder with Set-Cookie headers ready to be used.
     * @throws IllegalStateException if the user is not found.
     */
    fun createAuthenticatedSession(
        userId: UUID,
        ipAddress: String,
        userAgent: String,
        deviceFingerprint: String?,
        rememberDevice: Boolean,
        mfaUsed: Boolean,
        mfaMethod: String?,
        correlationId: UUID
    ): ResponseEntity.BodyBuilder {
        // Get the user
        val user = userRepository.findById(userId).orElseThrow {
            IllegalStateException("User not found after successful authentication: $userId")
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

        // Create device trust if requested and device fingerprint is available
        val deviceTrustCookie = if (rememberDevice && deviceFingerprint != null) {
            val deviceTrust = deviceTrustService.createTrust(
                userId = userId,
                deviceFingerprint = deviceFingerprint,
                userAgent = userAgent,
                ipAddress = ipAddress,
                correlationId = correlationId
            )
            logger.info("Created device trust {} for user {}", deviceTrust.id, userId)
            authCookieBuilder.buildDeviceTrustCookie(deviceTrust.id)
        } else {
            null
        }

        // Publish UserLoggedIn event
        val event = UserLoggedIn.create(
            userId = userId,
            sessionId = session.id,
            ipAddress = ipAddress,
            userAgent = userAgent,
            deviceFingerprint = deviceFingerprint,
            mfaUsed = mfaUsed,
            mfaMethod = mfaMethod,
            loginSource = "WEB",
            correlationId = correlationId
        )
        eventStoreRepository.append(event)
        userEventPublisher.publish(event)

        logger.info(
            "Created session {} and generated tokens for user {} (MFA used: {}, method: {})",
            session.id,
            userId,
            mfaUsed,
            mfaMethod
        )

        // Return response builder with cookies
        val response = ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, accessTokenCookie.toString())
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())

        // Add device trust cookie if created
        if (deviceTrustCookie != null) {
            response.header(HttpHeaders.SET_COOKIE, deviceTrustCookie.toString())
        }

        return response
    }
}
