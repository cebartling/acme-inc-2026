package com.acme.identity.api.v1

import com.acme.identity.api.v1.dto.ErrorResponse
import com.acme.identity.api.v1.dto.RegisterUserRequest
import com.acme.identity.api.v1.dto.RegisterUserResponse
import com.acme.identity.domain.UserStatus
import com.acme.identity.infrastructure.persistence.UserRepository
import com.acme.identity.infrastructure.security.RateLimiter
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class UserControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var rateLimiter: RateLimiter

    companion object {
        @Container
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("acme_identity_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("db/migration/init-test.sql")

        @Container
        val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"))

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.kafka.bootstrap-servers") { kafka.bootstrapServers }
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }
        }
    }

    @BeforeEach
    fun setUp() {
        userRepository.deleteAll()
        rateLimiter.reset("127.0.0.1")
    }

    @Test
    fun `POST register should create user and return 201`() {
        val request = createValidRequest()

        val result = mockMvc.perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.userId").exists())
            .andExpect(jsonPath("$.email").value(request.email.lowercase()))
            .andExpect(jsonPath("$.status").value(UserStatus.PENDING_VERIFICATION.name))
            .andExpect(jsonPath("$.createdAt").exists())
            .andReturn()

        val response = objectMapper.readValue(
            result.response.contentAsString,
            RegisterUserResponse::class.java
        )

        // Verify user was persisted
        val savedUser = userRepository.findById(response.userId)
        assertTrue(savedUser.isPresent)
        assertEquals(request.email.lowercase(), savedUser.get().email)
    }

    @Test
    fun `POST register with duplicate email should return 409`() {
        val request = createValidRequest()

        // First registration should succeed
        mockMvc.perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isCreated)

        // Second registration with same email should fail
        mockMvc.perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("DUPLICATE_EMAIL"))
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `POST register with invalid email should return 400`() {
        val request = createValidRequest().copy(email = "invalid-email")

        mockMvc.perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details[0].field").value("email"))
    }

    @Test
    fun `POST register with weak password should return 400`() {
        val request = createValidRequest().copy(password = "weak")

        mockMvc.perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details[?(@.field == 'password')]").exists())
    }

    @Test
    fun `POST register without TOS acceptance should return 400`() {
        val request = createValidRequest().copy(tosAccepted = false)

        mockMvc.perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details[?(@.field == 'tosAccepted')]").exists())
    }

    @Test
    fun `POST register exceeding rate limit should return 429`() {
        val baseRequest = createValidRequest()

        // Make 5 successful requests (within rate limit)
        repeat(5) { i ->
            val request = baseRequest.copy(email = "user$i@example.com")
            mockMvc.perform(
                post("/api/v1/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            ).andExpect(status().isCreated)
        }

        // 6th request should be rate limited
        val request = baseRequest.copy(email = "user6@example.com")
        mockMvc.perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"))
    }

    @Test
    fun `POST register should accept correlation ID header`() {
        val request = createValidRequest()
        val correlationId = "550e8400-e29b-41d4-a716-446655440000"

        mockMvc.perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-ID", correlationId)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `POST register should accept registration source header`() {
        val request = createValidRequest()

        mockMvc.perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Registration-Source", "MOBILE")
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)

        val user = userRepository.findByEmail(request.email.lowercase())
        assertNotNull(user)
        assertEquals(com.acme.identity.domain.RegistrationSource.MOBILE, user.registrationSource)
    }

    @Test
    fun `POST register with empty body should return 400`() {
        mockMvc.perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
    }

    private fun createValidRequest(): RegisterUserRequest {
        return RegisterUserRequest(
            email = "customer@example.com",
            password = "SecureP@ss123",
            firstName = "Jane",
            lastName = "Doe",
            tosAccepted = true,
            tosAcceptedAt = Instant.now(),
            marketingOptIn = false
        )
    }
}
