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
        assertNull(claims.audience)
    }

    @Test
    fun `tokens should be different on each call`() {
        val user = createTestUser()
        val tokens1 = tokenService.createTokens(user, testSessionId, testTokenFamily)
        val tokens2 = tokenService.createTokens(user, testSessionId, testTokenFamily)

        assertNotEquals(tokens1.accessToken, tokens2.accessToken)
        assertNotEquals(tokens1.refreshToken, tokens2.refreshToken)
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
