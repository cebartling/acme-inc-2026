package com.acme.identity.application

import com.acme.identity.domain.Session
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
 * Reuse detection is intentionally minimal in this commit: any mismatch
 * just returns `TokenReuse`. A follow-up commit adds Kafka-published
 * `TokenReuseDetected` events and sweeps every active session for the
 * affected user. The behavior here is already secure — the stale token is
 * rejected and no new tokens are issued — the follow-up only improves
 * observability and blast-radius cleanup.
 */
@Service
class RefreshTokensUseCase(
    private val tokenService: TokenService,
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
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
                "Refresh: tokenFamily mismatch for session {} (presented={}, session={})",
                sessionId, claimTokenFamily, session.tokenFamily
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

    private fun incrementCounter(result: String) {
        meterRegistry.counter("token_refresh_total", "result", result).increment()
    }
}
