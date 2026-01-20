package com.acme.identity.application

import com.acme.identity.domain.MfaChallenge
import com.acme.identity.domain.MfaMethod
import com.acme.identity.domain.events.MFAChallengeInitiated
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.EventStoreRepository
import com.acme.identity.infrastructure.persistence.MfaChallengeRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Service responsible for MFA challenge lifecycle management.
 *
 * Handles creation, deletion, and event publishing for MFA challenges.
 * This service is used by both AuthenticateUserUseCase and VerifyMfaUseCase
 * to avoid code duplication.
 */
@Service
class MfaChallengeService(
    private val mfaChallengeRepository: MfaChallengeRepository,
    private val eventStoreRepository: EventStoreRepository,
    private val userEventPublisher: UserEventPublisher
) {
    private val logger = LoggerFactory.getLogger(MfaChallengeService::class.java)

    /**
     * Creates a new MFA challenge for a user.
     *
     * This method:
     * 1. Deletes any existing challenges for the user (only one active challenge allowed)
     * 2. Creates and persists a new challenge
     * 3. Publishes an MFAChallengeInitiated event
     *
     * @param userId The user's ID.
     * @param method The MFA method to use (e.g., TOTP).
     * @param correlationId The correlation ID for distributed tracing.
     * @return The created MFA challenge.
     */
    @Transactional
    fun createChallenge(userId: UUID, method: MfaMethod, correlationId: UUID): MfaChallenge {
        // Delete any existing challenges for this user
        mfaChallengeRepository.deleteByUserId(userId)

        // Create new challenge
        val challenge = MfaChallenge.create(userId = userId, method = method)
        mfaChallengeRepository.save(challenge)

        // Publish event
        val event = MFAChallengeInitiated.create(
            userId = userId,
            mfaToken = challenge.token,
            method = method.name,
            expiresAt = challenge.expiresAt,
            correlationId = correlationId
        )
        eventStoreRepository.append(event)
        userEventPublisher.publishMFAChallengeInitiated(event)

        logger.info("Created MFA challenge for user {} with method {}", userId, method)

        return challenge
    }
}
