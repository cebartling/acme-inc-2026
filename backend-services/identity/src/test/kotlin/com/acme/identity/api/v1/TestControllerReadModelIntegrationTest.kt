package com.acme.identity.api.v1

import com.acme.identity.domain.RegistrationSource
import com.acme.identity.domain.User
import com.acme.identity.infrastructure.persistence.OutboxRepository
import com.acme.identity.infrastructure.persistence.UserRepository
import com.acme.identity.infrastructure.projection.UserReadModelDocument
import org.bson.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.mongodb.MongoDBContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

/**
 * The test API's part in the users read model (PIN-548): a status it sets directly writes a
 * snapshot, because no event does, and acceptance tests read the document back through it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class TestControllerReadModelIntegrationTest {

    companion object {
        @Container
        val postgresContainer = PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("acme_identity_test")
            .withUsername("test")
            .withPassword("test")

        @Container
        val kafkaContainer = KafkaContainer("apache/kafka:3.8.0")

        @Container
        val redisContainer = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)

        @Container
        val mongoContainer = MongoDBContainer("mongo:7.0")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgresContainer.jdbcUrl }
            registry.add("spring.datasource.username") { postgresContainer.username }
            registry.add("spring.datasource.password") { postgresContainer.password }
            registry.add("spring.kafka.bootstrap-servers") { kafkaContainer.bootstrapServers }
            registry.add("spring.data.redis.host") { redisContainer.host }
            registry.add("spring.data.redis.port") { redisContainer.getMappedPort(6379) }
            registry.add("spring.mongodb.uri") { mongoContainer.connectionString }
        }

        private const val API_KEY = "test-api-key-for-acceptance-tests"
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var outboxRepository: OutboxRepository

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    @BeforeEach
    fun setUp() {
        outboxRepository.deleteAll()
        userRepository.deleteAll()
        mongoTemplate.dropCollection(UserReadModelDocument.COLLECTION)
    }

    private fun newUser() = userRepository.saveAndFlush(
        User(
            id = UUID.randomUUID(),
            email = "read-model-${UUID.randomUUID()}@example.com",
            passwordHash = "hash",
            firstName = "Rhea",
            lastName = "Model",
            tosAcceptedAt = Instant.parse("2026-11-16T11:00:00Z"),
            registrationSource = RegistrationSource.WEB
        )
    )

    @Test
    fun `setting a user's status writes a snapshot of the new state to the outbox`() {
        val user = newUser()

        mockMvc.perform(
            post("/api/v1/test/users/${user.id}/status")
                .header("X-Test-Api-Key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"status":"SUSPENDED"}""")
        ).andExpect(status().isOk)

        val snapshot = outboxRepository.findAll().single()
        assertEquals("identity.read-model", snapshot.topic)
        assertEquals(user.id.toString(), snapshot.messageKey)
        assertEquals(true, snapshot.payload.contains("SUSPENDED"))
    }

    @Test
    fun `the read model endpoint answers 404 until the projector has written the user`() {
        mockMvc.perform(get("/api/v1/test/users/${UUID.randomUUID()}/read-model").header("X-Test-Api-Key", API_KEY))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `the read model endpoint returns the user's snapshot`() {
        val userId = UUID.randomUUID()
        mongoTemplate.getCollection(UserReadModelDocument.COLLECTION).insertOne(
            Document(UserReadModelDocument.ID, userId.toString())
                .append(
                    UserReadModelDocument.SNAPSHOT,
                    Document("userId", userId.toString()).append("status", "ACTIVE").append("emailVerified", true)
                )
        )

        mockMvc.perform(get("/api/v1/test/users/$userId/read-model").header("X-Test-Api-Key", API_KEY))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(userId.toString()))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.emailVerified").value(true))
    }
}
