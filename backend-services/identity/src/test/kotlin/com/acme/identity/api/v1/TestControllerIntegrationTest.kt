package com.acme.identity.api.v1

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.UUID

/**
 * The test API's key check (PIN-332): a missing or wrong `X-Test-Api-Key` is a 403, not the
 * generic 500 it used to fall through to.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class TestControllerIntegrationTest {

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

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgresContainer.jdbcUrl }
            registry.add("spring.datasource.username") { postgresContainer.username }
            registry.add("spring.datasource.password") { postgresContainer.password }
            registry.add("spring.kafka.bootstrap-servers") { kafkaContainer.bootstrapServers }
            registry.add("spring.data.redis.host") { redisContainer.host }
            registry.add("spring.data.redis.port") { redisContainer.getMappedPort(6379) }
            registry.add("acme.test-api.enabled") { "true" }
        }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val tokenPath = "/api/v1/test/users/${UUID.randomUUID()}/verification-token"

    @Test
    fun `a request without the test API key is a 403`() {
        mockMvc.perform(get(tokenPath))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value("FORBIDDEN"))
            .andExpect(jsonPath("$.message").value("Invalid or missing X-Test-Api-Key header"))
    }

    @Test
    fun `a request with the wrong test API key is a 403`() {
        mockMvc.perform(get(tokenPath).header("X-Test-Api-Key", "not-the-key"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value("FORBIDDEN"))
    }
}
