package com.acme.identity.application

import com.acme.identity.config.JwtConfig
import com.acme.identity.domain.User
import com.acme.identity.infrastructure.security.SigningKeyProvider
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.*

/**
 * Service for generating JWT access and refresh tokens.
 *
 * Responsibilities:
 * - Generate RS256-signed access tokens with user claims
 * - Generate RS256-signed refresh tokens for token rotation
 * - Include key ID (kid) in JWT header for key rotation support
 * - Set appropriate expiration times
 *
 * Token structure:
 * - Access Token: 15-minute expiry, contains userId, email, roles, sessionId
 * - Refresh Token: 7-day expiry, contains userId, sessionId, tokenFamily
 *
 * @property keyProvider Provides RSA signing keys for JWT signing.
 * @property config JWT configuration (issuer, audience, expiry times).
 */
@Service
class TokenService(
    private val keyProvider: SigningKeyProvider,
    private val config: JwtConfig
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Creates a pair of access and refresh tokens for the given user and session.
     *
     * The access token contains:
     * - sub: userId
     * - email: user's email address
     * - roles: user's roles (e.g., ["CUSTOMER"])
     * - sessionId: the session ID
     * - iat: issued at timestamp
     * - exp: expiration timestamp (15 minutes from now)
     * - iss: issuer (auth server URL)
     * - aud: audience (API server URL)
     *
     * The refresh token contains:
     * - sub: userId
     * - sessionId: the session ID
     * - tokenFamily: token family for rotation detection
     * - iat: issued at timestamp
     * - exp: expiration timestamp (7 days from now)
     * - iss: issuer (auth server URL)
     *
     * Both tokens are signed with RS256 using the current signing key.
     *
     * @param user The authenticated user.
     * @param sessionId The session ID for this authentication.
     * @param tokenFamily The token family ID for refresh token rotation.
     * @return A [TokenPair] containing both tokens and their expiry times.
     */
    fun createTokens(
        user: User,
        sessionId: String,
        tokenFamily: String
    ): TokenPair {
        val now = Instant.now()
        val signingKey = keyProvider.getCurrentKey()

        logger.debug("Generating tokens for user ${user.id} with key ID ${signingKey.keyId}")

        // Generate access token
        val accessToken = generateAccessToken(
            user = user,
            sessionId = sessionId,
            signingKey = signingKey,
            issuedAt = now
        )

        // Generate refresh token
        val refreshToken = generateRefreshToken(
            user = user,
            sessionId = sessionId,
            tokenFamily = tokenFamily,
            signingKey = signingKey,
            issuedAt = now
        )

        return TokenPair(
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpiry = config.accessTokenExpiry.seconds,
            refreshTokenExpiry = config.refreshTokenExpiry.seconds
        )
    }

    /**
     * Generates an RS256-signed access token JWT.
     *
     * @param user The user for whom the token is generated.
     * @param sessionId The session ID.
     * @param signingKey The RSA signing key.
     * @param issuedAt The token issuance timestamp.
     * @return The serialized JWT string.
     */
    private fun generateAccessToken(
        user: User,
        sessionId: String,
        signingKey: com.acme.identity.infrastructure.security.SigningKey,
        issuedAt: Instant
    ): String {
        val claims = JWTClaimsSet.Builder()
            .subject(user.id.toString())
            .claim("email", user.email)
            .claim("roles", listOf("CUSTOMER")) // User roles - hardcoded for now, can be expanded
            .claim("sessionId", sessionId)
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(issuedAt.plus(config.accessTokenExpiry)))
            .issuer(config.issuer)
            .audience(config.audience)
            .build()

        return signToken(claims, signingKey)
    }

    /**
     * Generates an RS256-signed refresh token JWT.
     *
     * @param user The user for whom the token is generated.
     * @param sessionId The session ID.
     * @param tokenFamily The token family for rotation detection.
     * @param signingKey The RSA signing key.
     * @param issuedAt The token issuance timestamp.
     * @return The serialized JWT string.
     */
    private fun generateRefreshToken(
        user: User,
        sessionId: String,
        tokenFamily: String,
        signingKey: com.acme.identity.infrastructure.security.SigningKey,
        issuedAt: Instant
    ): String {
        val claims = JWTClaimsSet.Builder()
            .subject(user.id.toString())
            .claim("sessionId", sessionId)
            .claim("tokenFamily", tokenFamily)
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(issuedAt.plus(config.refreshTokenExpiry)))
            .issuer(config.issuer)
            .build()

        return signToken(claims, signingKey)
    }

    /**
     * Signs a JWT claims set with RS256 using the provided signing key.
     *
     * The JWT header includes:
     * - alg: RS256
     * - typ: JWT
     * - kid: Key ID for key rotation support
     *
     * @param claims The JWT claims set.
     * @param key The signing key with private key and key ID.
     * @return The serialized, signed JWT string.
     */
    private fun signToken(
        claims: JWTClaimsSet,
        key: com.acme.identity.infrastructure.security.SigningKey
    ): String {
        val header = JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(key.keyId)
            .type(JOSEObjectType.JWT)
            .build()

        val jwt = SignedJWT(header, claims)
        jwt.sign(RSASSASigner(key.privateKey))

        return jwt.serialize()
    }

    /**
     * Parses and validates a JWT access token.
     *
     * Verifies:
     * - JWT signature using the signing key
     * - Token expiration
     * - Token issuer matches configuration
     *
     * @param token The JWT token string.
     * @return The user ID from the token subject, or null if invalid.
     */
    fun parseAccessToken(token: String): UUID? {
        return try {
            val jwt = SignedJWT.parse(token)
            val signingKey = keyProvider.getCurrentKey()

            // Verify signature
            val verifier = RSASSAVerifier(signingKey.publicKey as RSAPublicKey)
            if (!jwt.verify(verifier)) {
                logger.warn("JWT signature verification failed")
                return null
            }

            val claims = jwt.jwtClaimsSet

            // Verify expiration
            val expiration = claims.expirationTime
            if (expiration == null || expiration.before(Date())) {
                logger.debug("JWT token expired")
                return null
            }

            // Verify issuer
            if (claims.issuer != config.issuer) {
                logger.warn("JWT issuer mismatch: expected ${config.issuer}, got ${claims.issuer}")
                return null
            }

            // Extract user ID from subject
            val subject = claims.subject
            if (subject == null) {
                logger.warn("JWT token missing subject claim")
                return null
            }

            UUID.fromString(subject)
        } catch (e: Exception) {
            logger.warn("Failed to parse JWT token: ${e.message}")
            null
        }
    }
}
