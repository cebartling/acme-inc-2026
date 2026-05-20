package com.acme.identity.application

import com.acme.identity.domain.Session
import com.acme.identity.domain.events.SessionInvalidated
import com.acme.identity.domain.events.TokenReuseDetected
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.EventStoreRepository
import com.acme.identity.infrastructure.persistence.SessionRepository
import com.acme.identity.infrastructure.persistence.UserRepository
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Outcome of a refresh-token rotation attempt.
 *
 * The use case distinguishes failure modes internally so the controller can
 * decide how to shape the public response (currently any failure becomes
 * `401 TOKEN_EXPIRED` and clears all auth cookies). The reuse branch is
 * separated from generic invalid-token errors so a follow-up commit can
 * upgrade it to publish security events and sweep every session for the
 * user without churning this contract.
 */
sealed interface RefreshResult {
    /** Refresh succeeded; new tokens were issued and the session's tokenFamily was rotated. */
    data class Success(
        val tokens: TokenPair,
        val sessionId: String,
        val newTokenFamily: String
    ) : RefreshResult

    /** No refresh token was provided on the request. */
    data object MissingToken : RefreshResult

    /** The refresh JWT failed signature/issuer/expiry checks, or the user no longer exists. */
    data object InvalidToken : RefreshResult

    /** The session referenced by the refresh token's `sessionId` claim is no longer in Redis. */
    data object SessionNotFound : RefreshResult

    /**
     * The refresh JWT's `tokenFamily` claim does not match the session's
     * current tokenFamily — the token is a stale, already-rotated copy.
     * Indicates a likely replay attack.
     */
    data object TokenReuse : RefreshResult
}

/**
 * Validates an inbound refresh-token JWT and rotates the user's tokens.
 *
 * Happy path:
 *   1. Parse + verify the refresh JWT (signature, issuer, expiry).
 *   2. Look up the referenced [Session] by `sessionId` claim.
 *   3. Confirm the JWT's `tokenFamily` claim matches the session's current
 *      family — mismatch returns [RefreshResult.TokenReuse].
 *   4. Generate a new `tokenFamily`, issue a new [TokenPair], persist the
 *      updated session.
 *
 * Reuse detection follows OWASP guidance on refresh-token rotation:
 * presenting a stale (already-rotated) refresh token invalidates **every**
 * session for the user, not just the targeted one. A stolen refresh token
 * is evidence the attacker may have stolen others — nuking all sessions
 * forces re-authentication across devices and limits the blast radius.
 * Each invalidated session emits a [SessionInvalidated] event with
 * `reason=SECURITY`, plus a single [TokenReuseDetected] event captures
 * the reuse signal for security monitoring.
 */
@Service
class RefreshTokensUseCase(
    private val tokenService: TokenService,
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
    private val eventStoreRepository: EventStoreRepository,
    private val userEventPublisher: UserEventPublisher,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(RefreshTokensUseCase::class.java)

    fun execute(refreshToken: String?): RefreshResult {
        if (refreshToken.isNullOrBlank()) {
            incrementCounter("missing_token")
            return RefreshResult.MissingToken
        }

        val claims = tokenService.parseRefreshTokenClaims(refreshToken)
            ?: run {
                logger.debug("Refresh: token failed parse/verify")
                incrementCounter("invalid_token")
                return RefreshResult.InvalidToken
            }

        val subject = claims.subject
        val sessionId = claims.getStringClaim("sessionId")
        val claimTokenFamily = claims.getStringClaim("tokenFamily")

        if (subject.isNullOrBlank() || sessionId.isNullOrBlank() || claimTokenFamily.isNullOrBlank()) {
            logger.warn("Refresh: token missing required claims (sub/sessionId/tokenFamily)")
            incrementCounter("invalid_token")
            return RefreshResult.InvalidToken
        }

        val userId = try {
            UUID.fromString(subject)
        } catch (e: IllegalArgumentException) {
            logger.warn("Refresh: subject is not a valid UUID")
            incrementCounter("invalid_token")
            return RefreshResult.InvalidToken
        }

        val session = sessionRepository.findById(sessionId).orElse(null)
        if (session == null) {
            logger.debug("Refresh: session {} not found", sessionId)
            incrementCounter("session_not_found")
            return RefreshResult.SessionNotFound
        }

        if (session.tokenFamily != claimTokenFamily) {
            logger.warn(
                "Refresh: tokenFamily mismatch for session {} (presented={}, session={}) — " +
                        "invalidating every session for user {} per OWASP guidance",
                sessionId, claimTokenFamily, session.tokenFamily, userId
            )
            handleTokenReuse(
                triggeringSession = session,
                userId = userId,
                presentedTokenFamily = claimTokenFamily
            )
            incrementCounter("token_reuse")
            return RefreshResult.TokenReuse
        }

        val user = userRepository.findById(userId).orElse(null)
        if (user == null) {
            logger.warn("Refresh: user {} from valid session no longer exists", userId)
            incrementCounter("invalid_token")
            return RefreshResult.InvalidToken
        }

        val newTokenFamily = "fam_${UUID.randomUUID()}"
        val tokens = tokenService.createTokens(
            user = user,
            sessionId = sessionId,
            tokenFamily = newTokenFamily
        )

        sessionRepository.save(session.copy(tokenFamily = newTokenFamily))

        logger.info("Refresh: rotated tokens for session {} user {}", sessionId, userId)
        incrementCounter("success")
        return RefreshResult.Success(
            tokens = tokens,
            sessionId = sessionId,
            newTokenFamily = newTokenFamily
        )
    }

    /**
     * On reuse detection, sweep every active session for the user (OWASP)
     * and emit the matching events. Each session deletion publishes its own
     * [SessionInvalidated] event so existing consumers (security monitoring,
     * customer-facing notifications) keep working unchanged; a single
     * [TokenReuseDetected] event captures the security signal and the size
     * of the sweep.
     *
     * Designed to be best-effort: a Kafka failure here does NOT prevent the
     * security 401 response from going back to the client. The events are
     * persisted to the event store first, so even if Kafka is down the
     * audit trail is durable.
     */
    private fun handleTokenReuse(
        triggeringSession: Session,
        userId: UUID,
        presentedTokenFamily: String
    ) {
        val userSessions = sessionRepository.findByUserId(userId)
        var sessionsInvalidated = 0
        userSessions.forEach { s ->
            try {
                sessionRepository.delete(s)
            } catch (e: Exception) {
                // A delete failure during a reuse sweep is security-relevant:
                // a stolen refresh token can still rotate the un-deleted
                // session. Log loudly and skip the corresponding
                // SessionInvalidated event so downstream consumers aren't
                // told a session is gone when it isn't.
                logger.error(
                    "Failed to delete session {} during reuse sweep — session remains active: {}",
                    s.id, e.message, e
                )
                return@forEach
            }
            val invalidated = SessionInvalidated.create(
                sessionId = s.id,
                userId = userId,
                reason = SessionInvalidated.REASON_SECURITY
            )
            // Event store and Kafka publish failures are non-fatal: the
            // session is already deleted (durable), and the security 401
            // must still go back to the client. Catch and log so the
            // forEach doesn't bail mid-sweep.
            try {
                eventStoreRepository.append(invalidated)
            } catch (e: Exception) {
                logger.error(
                    "Failed to append SessionInvalidated event for session {}: {}",
                    s.id, e.message, e
                )
            }
            try {
                userEventPublisher.publish(invalidated)
            } catch (e: Exception) {
                logger.error(
                    "Failed to publish SessionInvalidated event for session {}: {}",
                    s.id, e.message, e
                )
            }
            sessionsInvalidated++
        }

        val reuseEvent = TokenReuseDetected.create(
            sessionId = triggeringSession.id,
            userId = userId,
            sessionTokenFamily = triggeringSession.tokenFamily,
            presentedTokenFamily = presentedTokenFamily,
            sessionsInvalidatedCount = sessionsInvalidated
        )
        try {
            eventStoreRepository.append(reuseEvent)
        } catch (e: Exception) {
            logger.error(
                "Failed to append TokenReuseDetected event for session {}: {}",
                triggeringSession.id, e.message, e
            )
        }
        try {
            userEventPublisher.publishTokenReuseDetected(reuseEvent)
        } catch (e: Exception) {
            logger.error(
                "Failed to publish TokenReuseDetected event for session {}: {}",
                triggeringSession.id, e.message, e
            )
        }
    }

    private fun incrementCounter(result: String) {
        meterRegistry.counter("token_refresh_total", "result", result).increment()
    }
}
