package com.acme.identity.application

import com.acme.identity.domain.ReactivationToken
import com.acme.identity.domain.UserStatus
import com.acme.identity.domain.events.ReactivationRequested
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.EventStoreRepository
import com.acme.identity.infrastructure.persistence.ReactivationTokenRepository
import com.acme.identity.infrastructure.persistence.UserRepository
import com.acme.identity.infrastructure.security.PasswordHasher
import com.acme.identity.infrastructure.security.ReactivationTokenGenerator
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Outcome of a reactivation request. The caller always receives a
 * [Success] from the controller for invalid inputs — this internal
 * distinction lets us count metrics accurately without leaking
 * information to the API consumer.
 */
sealed interface ReactivateResult {
    /** Reactivation email was generated (or pretended to be, for unknown users). */
    data object Success : ReactivateResult
}

/**
 * Issues a single-use reactivation token to a DEACTIVATED user after
 * verifying their current password, and emits a [ReactivationRequested]
 * event for the notification service to send the email.
 *
 * For security:
 * - Response is identical whether the email maps to a real user, an
 *   ACTIVE/PENDING_VERIFICATION/SUSPENDED user, or no user at all.
 *   This prevents enumeration of which accounts are deactivated.
 * - A dummy password verification runs whenever we cannot verify a real
 *   one, keeping response times comparable across paths and resisting
 *   timing attacks.
 */
@Service
class ReactivateAccountUseCase(
    private val userRepository: UserRepository,
    private val reactivationTokenRepository: ReactivationTokenRepository,
    private val eventStoreRepository: EventStoreRepository,
    private val userEventPublisher: UserEventPublisher,
    private val passwordHasher: PasswordHasher,
    private val reactivationTokenGenerator: ReactivationTokenGenerator,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(ReactivateAccountUseCase::class.java)

    private val dummyPasswordHash: String by lazy {
        passwordHasher.hash("dummy_password_for_timing_attack_prevention")
    }

    @Transactional
    fun execute(
        email: String,
        password: String,
        correlationId: UUID = UUID.randomUUID()
    ): ReactivateResult {
        val normalizedEmail = email.lowercase()
        val user = userRepository.findByEmail(normalizedEmail)

        if (user == null) {
            // Run dummy hash to keep response time consistent.
            passwordHasher.verify(password, dummyPasswordHash)
            logger.debug("Reactivate request for non-existent email")
            incrementCounter("unknown_email")
            return ReactivateResult.Success
        }

        if (user.status != UserStatus.DEACTIVATED) {
            // Don't reveal status. Still hash-verify so timing matches.
            passwordHasher.verify(password, user.passwordHash)
            logger.debug("Reactivate request for non-deactivated user: {}", user.id)
            incrementCounter("not_deactivated")
            return ReactivateResult.Success
        }

        val passwordMatches = passwordHasher.verify(password, user.passwordHash)
        if (!passwordMatches) {
            logger.debug("Reactivate request with wrong password for user: {}", user.id)
            incrementCounter("wrong_password")
            return ReactivateResult.Success
        }

        val token = ReactivationToken(
            id = UUID.randomUUID(),
            userId = user.id,
            token = reactivationTokenGenerator.generate(),
            expiresAt = reactivationTokenGenerator.calculateExpiration()
        )
        reactivationTokenRepository.save(token)

        val event = ReactivationRequested.create(
            userId = user.id,
            email = user.email,
            firstName = user.firstName,
            lastName = user.lastName,
            reactivationToken = token.token,
            expiresAt = token.expiresAt,
            correlationId = correlationId
        )
        eventStoreRepository.append(event)
        userEventPublisher.publishReactivationRequested(event)

        logger.info("Reactivation email triggered for user: {}", user.id)
        incrementCounter("email_sent")
        return ReactivateResult.Success
    }

    private fun incrementCounter(result: String) {
        meterRegistry.counter("reactivation_request_total", "result", result).increment()
    }
}
