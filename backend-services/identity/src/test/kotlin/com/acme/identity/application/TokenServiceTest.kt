package com.acme.identity.application

import com.acme.identity.config.JwtConfig
import com.acme.identity.domain.User
import com.acme.identity.domain.UserStatus
import com.acme.identity.infrastructure.security.SigningKeyProvider
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class TokenServiceTest {

    private lateinit var signingKeyProvider: SigningKeyProvider
    private lateinit var jwtConfig: JwtConfig
    private lateinit var tokenService: TokenService

    private val testUserId = UUID.randomUUID()
    private val testSessionId = "sess_${UUID.randomUUID()}"
    private val testTokenFamily = "fam_${UUID.randomUUID()}"

    @BeforeEach
    fun setUp() {
        jwtConfig = JwtConfig(
            issuer = "https://auth.acme.com",
            audience = "https://api.acme.com",
            accessTokenExpiryMinutes = 15,
            refreshTokenExpiryDays = 7,
            keyRotationPeriodDays = 30
        )
        signingKeyProvider = SigningKeyProvider(jwtConfig)
        tokenService = TokenService(signingKeyProvider, jwtConfig)
    }

    @Test
    fun `createTokens should generate both access and refresh tokens`() {
        val user = createTestUser()
        val tokens = tokenService.createTokens(user, testSessionId, testTokenFamily)

        assertNotNull(tokens.accessToken)
        assertNotNull(tokens.refreshToken)
        assertTrue(tokens.accessToken.isNotBlank())
        assertTrue(tokens.refreshToken.isNotBlank())
    }

    @Test
    fun `createTokens should return correct expiry times`() {
        val user = createTestUser()
        val tokens = tokenService.createTokens(user, testSessionId, testTokenFamily)

        assertEquals(900L, tokens.accessTokenExpiry) // 15 minutes
        assertEquals(604800L, tokens.refreshTokenExpiry) // 7 days
    }

    @Test
    fun `access token should have correct structure`() {
        val user = createTestUser()
        val tokens = tokenService.createTokens(user, testSessionId, testTokenFamily)

        val jwt = SignedJWT.parse(tokens.accessToken)
        val header = jwt.header
        val claims = jwt.jwtClaimsSet

        // Verify header
        assertEquals("RS256", header.algorithm.name)
        assertEquals("JWT", header.type.type)
        assertNotNull(header.keyID)
        assertTrue(header.keyID.startsWith("key-"))

        // Verify claims
        assertEquals(testUserId.toString(), claims.subject)
        assertEquals(user.email, claims.getStringClaim("email"))
        assertEquals(listOf("CUSTOMER"), claims.getStringListClaim("roles"))
        assertEquals(testSessionId, claims.getStringClaim("sessionId"))
        assertEquals(jwtConfig.issuer, claims.issuer)
        assertEquals(jwtConfig.audience, claims.audience.firstOrNull())
        assertNotNull(claims.issueTime)
        assertNotNull(claims.expirationTime)
    }

    @Test
    fun `refresh token should have correct structure`() {
        val user = createTestUser()
        val tokens = tokenService.createTokens(user, testSessionId, testTokenFamily)

        val jwt = SignedJWT.parse(tokens.refreshToken)
        val header = jwt.header
        val claims = jwt.jwtClaimsSet

        // Verify header
        assertEquals("RS256", header.algorithm.name)
        assertEquals("JWT", header.type.type)
        assertNotNull(header.keyID)

        // Verify claims
        assertEquals(testUserId.toString(), claims.subject)
        assertEquals(testSessionId, claims.getStringClaim("sessionId"))
        assertEquals(testTokenFamily, claims.getStringClaim("tokenFamily"))
        assertEquals(jwtConfig.issuer, claims.issuer)
        assertNotNull(claims.issueTime)
        assertNotNull(claims.expirationTime)
    }

    @Test
    fun `access token should expire in 15 minutes`() {
        val user = createTestUser()
        val tokens = tokenService.createTokens(user, testSessionId, testTokenFamily)

        val jwt = SignedJWT.parse(tokens.accessToken)
        val claims = jwt.jwtClaimsSet

        val iat = claims.issueTime.toInstant()
        val exp = claims.expirationTime.toInstant()
        val duration = java.time.Duration.between(iat, exp)

        assertEquals(900L, duration.seconds) // 15 minutes
    }

    @Test
    fun `refresh token should expire in 7 days`() {
        val user = createTestUser()
        val tokens = tokenService.createTokens(user, testSessionId, testTokenFamily)

        val jwt = SignedJWT.parse(tokens.refreshToken)
        val claims = jwt.jwtClaimsSet

        val iat = claims.issueTime.toInstant()
        val exp = claims.expirationTime.toInstant()
        val duration = java.time.Duration.between(iat, exp)

        assertEquals(604800L, duration.seconds) // 7 days
    }

    @Test
    fun `tokens should be signed with current signing key`() {
        val user = createTestUser()
        val currentKey = signingKeyProvider.getCurrentKey()
        val tokens = tokenService.createTokens(user, testSessionId, testTokenFamily)

        val accessJwt = SignedJWT.parse(tokens.accessToken)
        val refreshJwt = SignedJWT.parse(tokens.refreshToken)

        assertEquals(currentKey.keyId, accessJwt.header.keyID)
        assertEquals(currentKey.keyId, refreshJwt.header.keyID)
    }

    @Test
    fun `tokens should be verifiable with public key`() {
        val user = createTestUser()
        val signingKey = signingKeyProvider.getCurrentKey()
        val tokens = tokenService.createTokens(user, testSessionId, testTokenFamily)

        val accessJwt = SignedJWT.parse(tokens.accessToken)
        val refreshJwt = SignedJWT.parse(tokens.refreshToken)

        // Verify signatures with public key
        val verifier = com.nimbusds.jose.crypto.RSASSAVerifier(signingKey.publicKey as java.security.interfaces.RSAPublicKey)
        assertTrue(accessJwt.verify(verifier))
        assertTrue(refreshJwt.verify(verifier))
    }

    @Test
    fun `access token should not include refresh token claims`() {
        val user = createTestUser()
        val tokens = tokenService.createTokens(user, testSessionId, testTokenFamily)

        val jwt = SignedJWT.parse(tokens.accessToken)
        val claims = jwt.jwtClaimsSet

        assertNull(claims.getStringClaim("tokenFamily"))
    }

    @Test
    fun `refresh token should not include access token claims`() {
        val user = createTestUser()
        val tokens = tokenService.createTokens(user, testSessionId, testTokenFamily)

        val jwt = SignedJWT.parse(tokens.refreshToken)
        val claims = jwt.jwtClaimsSet

        assertNull(claims.getStringClaim("email"))
        assertNull(claims.getStringListClaim("roles"))
        assertTrue(claims.audience.isNullOrEmpty(), "Refresh token should not have audience claim")
    }

    @Test
    fun `tokens should be different on each call`() {
        val user = createTestUser()
        val tokens1 = tokenService.createTokens(user, testSessionId, testTokenFamily)
        Thread.sleep(1000) // Ensure different timestamp
        val tokens2 = tokenService.createTokens(user, testSessionId, testTokenFamily)

        assertNotEquals(tokens1.accessToken, tokens2.accessToken)
        assertNotEquals(tokens1.refreshToken, tokens2.refreshToken)
    }

    @Test
    fun `parseAccessTokenClaims should return full claims for a valid token`() {
        val user = createTestUser()
        val tokens = tokenService.createTokens(user, testSessionId, testTokenFamily)

        val claims = tokenService.parseAccessTokenClaims(tokens.accessToken)

        assertNotNull(claims)
        assertEquals(testUserId.toString(), claims.subject)
        assertEquals(testSessionId, claims.getStringClaim("sessionId"))
        assertEquals(user.email, claims.getStringClaim("email"))
        assertEquals(jwtConfig.issuer, claims.issuer)
    }

    @Test
    fun `parseAccessTokenClaims should return null for a malformed token`() {
        val claims = tokenService.parseAccessTokenClaims("not.a.jwt")
        assertNull(claims)
    }

    @Test
    fun `parseAccessTokenClaims should return null when issuer does not match`() {
        val otherConfig = JwtConfig(
            issuer = "https://other.acme.com",
            audience = jwtConfig.audience,
            accessTokenExpiryMinutes = 15,
            refreshTokenExpiryDays = 7,
            keyRotationPeriodDays = 30
        )
        val otherTokenService = TokenService(signingKeyProvider, otherConfig)
        val user = createTestUser()
        val tokens = otherTokenService.createTokens(user, testSessionId, testTokenFamily)

        val claims = tokenService.parseAccessTokenClaims(tokens.accessToken)

        assertNull(claims)
    }

    @Test
    fun `parseAccessTokenClaims should return null for an expired token`() {
        val shortLivedConfig = JwtConfig(
            issuer = jwtConfig.issuer,
            audience = jwtConfig.audience,
            accessTokenExpiryMinutes = 0,
            refreshTokenExpiryDays = 7,
            keyRotationPeriodDays = 30
        )
        val shortLivedService = TokenService(signingKeyProvider, shortLivedConfig)
        val user = createTestUser()
        val tokens = shortLivedService.createTokens(user, testSessionId, testTokenFamily)

        Thread.sleep(1100)

        val claims = tokenService.parseAccessTokenClaims(tokens.accessToken)
        assertNull(claims)
    }

    @Test
    fun `parseRefreshTokenClaims accepts a token signed under the previous key after rotation`() {
        // Real-world value of multi-key verification: a refresh token issued
        // before a key rotation (which happens every 30 days) must still
        // validate. Without this, every rotation evicts every active
        // 7-day-TTL session.
        val user = createTestUser()
        val tokensBeforeRotation = tokenService.createTokens(user, testSessionId, testTokenFamily)

        signingKeyProvider.rotateKey()

        val claims = tokenService.parseRefreshTokenClaims(tokensBeforeRotation.refreshToken)
        assertNotNull(claims, "refresh token from prior key should still verify after rotation")
        assertEquals(user.id.toString(), claims.subject)
        assertEquals(testTokenFamily, claims.getStringClaim("tokenFamily"))
    }

    @Test
    fun `parseAccessTokenClaims should reject a refresh token (token_use mismatch)`() {
        val user = createTestUser()
        val tokens = tokenService.createTokens(user, testSessionId, testTokenFamily)

        // Presenting the refresh-token JWT at an access-token verification
        // point must fail — defense-in-depth against token confusion.
        val claims = tokenService.parseAccessTokenClaims(tokens.refreshToken)

        assertNull(claims)
    }

    @Test
    fun `parseRefreshTokenClaims should reject an access token (token_use mismatch)`() {
        val user = createTestUser()
        val tokens = tokenService.createTokens(user, testSessionId, testTokenFamily)

        // The symmetric guard: an access-token JWT presented at the refresh
        // endpoint must fail even though it shares issuer and signing key.
        val claims = tokenService.parseRefreshTokenClaims(tokens.accessToken)

        assertNull(claims)
    }

    private fun createTestUser(): User {
        return User(
            id = testUserId,
            email = "test@example.com",
            passwordHash = "hashed_password",
            firstName = "Test",
            lastName = "User",
            status = UserStatus.ACTIVE,
            tosAcceptedAt = Instant.now(),
            registrationSource = com.acme.identity.domain.RegistrationSource.WEB,
            emailVerified = true,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
}
