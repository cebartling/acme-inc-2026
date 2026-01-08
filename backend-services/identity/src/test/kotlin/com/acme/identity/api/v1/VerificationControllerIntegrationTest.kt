package com.acme.identity.api.v1

import com.acme.identity.api.v1.dto.ResendVerificationRequest
import com.acme.identity.domain.RegistrationSource
import com.acme.identity.domain.User
import com.acme.identity.domain.UserStatus
import com.acme.identity.domain.VerificationToken
import com.acme.identity.infrastructure.persistence.ResendRequestRepository
import com.acme.identity.infrastructure.persistence.UserRepository
import com.acme.identity.infrastructure.persistence.VerificationTokenRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class VerificationControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var verificationTokenRepository: VerificationTokenRepository

    @Autowired
    private lateinit var resendRequestRepository: ResendRequestRepository

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("acme_identity_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("db/migration/init-test.sql")

        @Container
        val kafka = KafkaContainer("apache/kafka:3.8.0")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.kafka.bootstrap-servers") { kafka.bootstrapServers }
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }
            registry.add("identity.frontend.base-url") { "https://www.acme.com" }
        }
    }

    @BeforeEach
    fun setUp() {
        resendRequestRepository.deleteAll()
        verificationTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    // ===== Verify Email Tests =====

    @Test
    fun `GET verify with valid token should redirect to login with success`() {
        // Given
        val user = createAndSavePendingUser()
        val token = createAndSaveValidToken(user.id)

        // When/Then
        mockMvc.perform(get("/api/v1/users/verify").param("token", token.token))
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "https://www.acme.com/login?verified=true"))

        // Verify user is now active
        val updatedUser = userRepository.findById(user.id).get()
        assertEquals(UserStatus.ACTIVE, updatedUser.status)
        assertTrue(updatedUser.emailVerified)
        assertNotNull(updatedUser.verifiedAt)
    }

    @Test
    fun `GET verify with expired token should redirect to resend page with error`() {
        // Given
        val user = createAndSavePendingUser()
        val token = createAndSaveExpiredToken(user.id)

        // When/Then
        mockMvc.perform(get("/api/v1/users/verify").param("token", token.token))
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "https://www.acme.com/verify/resend?error=expired"))

        // Verify user is still pending
        val updatedUser = userRepository.findById(user.id).get()
        assertEquals(UserStatus.PENDING_VERIFICATION, updatedUser.status)
    }

    @Test
    fun `GET verify with invalid token should redirect to resend page with error`() {
        // When/Then
        mockMvc.perform(get("/api/v1/users/verify").param("token", "invalid-token-xyz"))
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "https://www.acme.com/verify/resend?error=invalid"))
    }

    @Test
    fun `GET verify with already used token should redirect to login with already verified`() {
        // Given
        val user = createAndSavePendingUser()
        val token = createAndSaveUsedToken(user.id)

        // When/Then
        mockMvc.perform(get("/api/v1/users/verify").param("token", token.token))
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "https://www.acme.com/login?already_verified=true"))
    }

    @Test
    fun `GET verify for already verified user should redirect to login`() {
        // Given
        val user = createAndSaveVerifiedUser()
        val token = createAndSaveValidToken(user.id)

        // When/Then
        mockMvc.perform(get("/api/v1/users/verify").param("token", token.token))
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "https://www.acme.com/login?already_verified=true"))
    }

    // ===== Resend Verification Tests =====

    @Test
    fun `POST resend should return success for valid pending user`() {
        // Given
        val user = createAndSavePendingUser()
        val request = ResendVerificationRequest(email = user.email)

        // When/Then
        mockMvc.perform(
            post("/api/v1/users/verify/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("If an account exists with this email, a verification link has been sent."))
            .andExpect(jsonPath("$.requestsRemaining").exists())

        // Verify a new token was created
        val tokens = verificationTokenRepository.findByUserId(user.id)
        assertEquals(1, tokens.size)
    }

    @Test
    fun `POST resend should return success for nonexistent email for security`() {
        // Given
        val request = ResendVerificationRequest(email = "nonexistent@example.com")

        // When/Then
        mockMvc.perform(
            post("/api/v1/users/verify/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("If an account exists with this email, a verification link has been sent."))
    }

    @Test
    fun `POST resend should return success for already verified user for security`() {
        // Given
        val user = createAndSaveVerifiedUser()
        val request = ResendVerificationRequest(email = user.email)

        // When/Then
        mockMvc.perform(
            post("/api/v1/users/verify/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("If an account exists with this email, a verification link has been sent."))
    }

    @Test
    fun `POST resend should return 429 when rate limit exceeded`() {
        // Given - make 3 requests first (rate limit is 3 per hour)
        val user = createAndSavePendingUser()
        val request = ResendVerificationRequest(email = user.email)

        repeat(3) {
            mockMvc.perform(
                post("/api/v1/users/verify/resend")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            ).andExpect(status().isOk)
        }

        // When/Then - 4th request should be rate limited
        mockMvc.perform(
            post("/api/v1/users/verify/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"))
            .andExpect(header().exists("Retry-After"))
    }

    @Test
    fun `POST resend with invalid email format should return 400`() {
        // Given
        val request = ResendVerificationRequest(email = "invalid-email")

        // When/Then
        mockMvc.perform(
            post("/api/v1/users/verify/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
    }

    @Test
    fun `POST resend with empty email should return 400`() {
        // Given
        val request = ResendVerificationRequest(email = "")

        // When/Then
        mockMvc.perform(
            post("/api/v1/users/verify/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
    }

    // ===== Helper Methods =====

    private fun createAndSavePendingUser(): User {
        val user = User(
            id = UUID.randomUUID(),
            email = "customer-${UUID.randomUUID()}@example.com",
            passwordHash = "\$argon2id\$v=19\$m=65536,t=3,p=4\$somesalt\$somehash",
            firstName = "Jane",
            lastName = "Doe",
            status = UserStatus.PENDING_VERIFICATION,
            tosAcceptedAt = Instant.now(),
            registrationSource = RegistrationSource.WEB
        )
        return userRepository.save(user)
    }

    private fun createAndSaveVerifiedUser(): User {
        val user = User(
            id = UUID.randomUUID(),
            email = "verified-${UUID.randomUUID()}@example.com",
            passwordHash = "\$argon2id\$v=19\$m=65536,t=3,p=4\$somesalt\$somehash",
            firstName = "Jane",
            lastName = "Doe",
            status = UserStatus.ACTIVE,
            tosAcceptedAt = Instant.now(),
            registrationSource = RegistrationSource.WEB,
            emailVerified = true,
            verifiedAt = Instant.now().minusSeconds(3600)
        )
        return userRepository.save(user)
    }

    private fun createAndSaveValidToken(userId: UUID): VerificationToken {
        val token = VerificationToken(
            id = UUID.randomUUID(),
            userId = userId,
            token = "valid-token-${UUID.randomUUID()}",
            expiresAt = Instant.now().plusSeconds(86400) // 24 hours
        )
        return verificationTokenRepository.save(token)
    }

    private fun createAndSaveExpiredToken(userId: UUID): VerificationToken {
        val token = VerificationToken(
            id = UUID.randomUUID(),
            userId = userId,
            token = "expired-token-${UUID.randomUUID()}",
            expiresAt = Instant.now().minusSeconds(3600) // Expired 1 hour ago
        )
        return verificationTokenRepository.save(token)
    }

    private fun createAndSaveUsedToken(userId: UUID): VerificationToken {
        val token = VerificationToken(
            id = UUID.randomUUID(),
            userId = userId,
            token = "used-token-${UUID.randomUUID()}",
            expiresAt = Instant.now().plusSeconds(86400),
            usedAt = Instant.now().minusSeconds(1800) // Used 30 minutes ago
        )
        return verificationTokenRepository.save(token)
    }
}
