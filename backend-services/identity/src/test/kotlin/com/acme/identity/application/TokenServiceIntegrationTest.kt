package com.acme.identity.application

import com.acme.identity.config.JwtConfig
import com.acme.identity.domain.User
import com.acme.identity.domain.UserStatus
import com.acme.identity.infrastructure.security.SigningKeyProvider
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID
import kotlin.test.*

@SpringBootTest
@ActiveProfiles("test")
class TokenServiceIntegrationTest {

    @Autowired
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var signingKeyProvider: SigningKeyProvider

    @Autowired
    private lateinit var jwtConfig: JwtConfig

    private val testUserId = UUID.randomUUID()
    private val testSessionId = "sess_${UUID.randomUUID()}"
    private val testTokenFamily = "fam_${UUID.randomUUID()}"

    @Test
    fun `createTokens should generate valid JWTs`() {
        val user = createTestUser()
        val tokens = tokenService.createTokens(user, testSessionId, testTokenFamily)

        assertNotNull(tokens.accessToken)
        assertNotNull(tokens.refreshToken)

        // Parse tokens to verify they are valid JWTs
        val accessJwt = SignedJWT.parse(tokens.accessToken)
        val refreshJwt = SignedJWT.parse(tokens.refreshToken)

        assertNotNull(accessJwt)
        assertNotNull(refreshJwt)
    }

    @Test
    fun `generated tokens should be verifiable with public key`() {
        val user = createTestUser()
        val signingKey = signingKeyProvider.getCurrentKey()
        val tokens = tokenService.createTokens(user, testSessionId, testTokenFamily)

        val accessJwt = SignedJWT.parse(tokens.accessToken)
        val refreshJwt = SignedJWT.parse(tokens.refreshToken)

        val verifier = RSASSAVerifier(signingKey.publicKey)

        assertTrue(accessJwt.verify(verifier), "Access token signature should be valid")
        assertTrue(refreshJwt.verify(verifier), "Refresh token signature should be valid")
    }

    @Test
    fun `access token should contain all required claims`() {
        val user = createTestUser()
        val tokens = tokenService.createTokens(user, testSessionId, testTokenFamily)

        val jwt = SignedJWT.parse(tokens.accessToken)
        val claims = jwt.jwtClaimsSet

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
    fun `refresh token should contain all required claims`() {
        val user = createTestUser()
        val tokens = tokenService.createTokens(user, testSessionId, testTokenFamily)

        val jwt = SignedJWT.parse(tokens.refreshToken)
        val claims = jwt.jwtClaimsSet

        assertEquals(testUserId.toString(), claims.subject)
        assertEquals(testSessionId, claims.getStringClaim("sessionId"))
        assertEquals(testTokenFamily, claims.getStringClaim("tokenFamily"))
        assertEquals(jwtConfig.issuer, claims.issuer)
        assertNotNull(claims.issueTime)
        assertNotNull(claims.expirationTime)
    }

    @Test
    fun `access token expiration should match configuration`() {
        val user = createTestUser()
        val tokens = tokenService.createTokens(user, testSessionId, testTokenFamily)

        val jwt = SignedJWT.parse(tokens.accessToken)
        val claims = jwt.jwtClaimsSet

        val iat = claims.issueTime.toInstant()
        val exp = claims.expirationTime.toInstant()
        val duration = java.time.Duration.between(iat, exp)

        assertEquals(jwtConfig.accessTokenExpiry.seconds, duration.seconds)
    }

    @Test
    fun `refresh token expiration should match configuration`() {
        val user = createTestUser()
        val tokens = tokenService.createTokens(user, testSessionId, testTokenFamily)

        val jwt = SignedJWT.parse(tokens.refreshToken)
        val claims = jwt.jwtClaimsSet

        val iat = claims.issueTime.toInstant()
        val exp = claims.expirationTime.toInstant()
        val duration = java.time.Duration.between(iat, exp)

        assertEquals(jwtConfig.refreshTokenExpiry.seconds, duration.seconds)
    }

    @Test
    fun `tokens should have correct key ID in header`() {
        val user = createTestUser()
        val currentKey = signingKeyProvider.getCurrentKey()
        val tokens = tokenService.createTokens(user, testSessionId, testTokenFamily)

        val accessJwt = SignedJWT.parse(tokens.accessToken)
        val refreshJwt = SignedJWT.parse(tokens.refreshToken)

        assertEquals(currentKey.keyId, accessJwt.header.keyID)
        assertEquals(currentKey.keyId, refreshJwt.header.keyID)
    }

    @Test
    fun `tokens should have RS256 algorithm in header`() {
        val user = createTestUser()
        val tokens = tokenService.createTokens(user, testSessionId, testTokenFamily)

        val accessJwt = SignedJWT.parse(tokens.accessToken)
        val refreshJwt = SignedJWT.parse(tokens.refreshToken)

        assertEquals("RS256", accessJwt.header.algorithm.name)
        assertEquals("RS256", refreshJwt.header.algorithm.name)
    }

    @Test
    fun `tokens should have JWT type in header`() {
        val user = createTestUser()
        val tokens = tokenService.createTokens(user, testSessionId, testTokenFamily)

        val accessJwt = SignedJWT.parse(tokens.accessToken)
        val refreshJwt = SignedJWT.parse(tokens.refreshToken)

        assertEquals("JWT", accessJwt.header.type.type)
        assertEquals("JWT", refreshJwt.header.type.type)
    }

    @Test
    fun `concurrent token generation should produce different tokens`() {
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
            emailVerified = true,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
}
