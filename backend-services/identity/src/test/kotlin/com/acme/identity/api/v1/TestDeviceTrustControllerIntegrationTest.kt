package com.acme.identity.api.v1

import com.acme.identity.infrastructure.persistence.DeviceTrustRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterEach
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.testcontainers.containers.GenericContainer
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.*

/**
 * Integration tests for TestDeviceTrustController.
 *
 * These tests verify the test API endpoints work correctly with real
 * Redis and PostgreSQL (via Testcontainers).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class TestDeviceTrustControllerIntegrationTest {

    companion object {
        @Container
        val postgresContainer = PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("acme_identity_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("db/migration/init-test.sql")

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

    @Autowired
    private lateinit var deviceTrustRepository: DeviceTrustRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var testUserId: UUID

    @BeforeEach
    fun setup() {
        testUserId = UUID.randomUUID()
        // Clean up any existing device trusts
        deviceTrustRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        deviceTrustRepository.deleteAll()
    }

    // ========================================================================
    // POST /api/v1/test/users/:userId/device-trusts
    // ========================================================================

    @Test
    fun `createDeviceTrust should create device trust with provided parameters`() {
        val deviceFingerprint = "fp_test_device"
        val userAgent = "Mozilla/5.0 (Test Browser)"
        val ipAddress = "192.168.1.100"

        val result = mockMvc.perform(
            post("/api/v1/test/users/$testUserId/device-trusts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "deviceFingerprint": "$deviceFingerprint",
                      "userAgent": "$userAgent",
                      "ipAddress": "$ipAddress"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.deviceTrustId").exists())
            .andReturn()

        // Verify device trust was created in Redis
        val response = objectMapper.readTree(result.response.contentAsString)
        val deviceTrustId = response.get("deviceTrustId").asText()

        val deviceTrust = deviceTrustRepository.findById(deviceTrustId)
        assert(deviceTrust.isPresent)
        assert(deviceTrust.get().userId == testUserId)
    }

    @Test
    fun `createDeviceTrust should accept custom TTL in seconds`() {
        val customTtl = 100L

        mockMvc.perform(
            post("/api/v1/test/users/$testUserId/device-trusts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "deviceFingerprint": "fp_test",
                      "userAgent": "Mozilla/5.0",
                      "ipAddress": "192.168.1.1",
                      "ttlSeconds": $customTtl
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.deviceTrustId").exists())
    }

    @Test
    fun `createDeviceTrust should accept custom device trust ID`() {
        val customId = "trust_custom_456"

        mockMvc.perform(
            post("/api/v1/test/users/$testUserId/device-trusts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "deviceFingerprint": "fp_test",
                      "userAgent": "Mozilla/5.0",
                      "ipAddress": "192.168.1.1",
                      "deviceTrustId": "$customId"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.deviceTrustId").value(customId))

        // Verify the custom ID was used
        val deviceTrust = deviceTrustRepository.findById(customId)
        assert(deviceTrust.isPresent)
    }

    @Test
    fun `createDeviceTrust should accept createdDaysAgo parameter`() {
        val daysAgo = 5

        mockMvc.perform(
            post("/api/v1/test/users/$testUserId/device-trusts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "deviceFingerprint": "fp_old",
                      "userAgent": "Mozilla/5.0",
                      "ipAddress": "192.168.1.1",
                      "createdDaysAgo": $daysAgo
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.deviceTrustId").exists())
    }

    // ========================================================================
    // GET /api/v1/test/users/:userId/device-trusts
    // ========================================================================

    @Test
    fun `getDeviceTrusts should return all device trusts for user`() {
        // Create two device trusts
        createDeviceTrust(testUserId, "fp_1")
        createDeviceTrust(testUserId, "fp_2")

        mockMvc.perform(get("/api/v1/test/users/$testUserId/device-trusts"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.devices").isArray)
            .andExpect(jsonPath("$.devices.length()").value(2))
    }

    @Test
    fun `getDeviceTrusts should return empty array when user has no device trusts`() {
        mockMvc.perform(get("/api/v1/test/users/$testUserId/device-trusts"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.devices").isArray)
            .andExpect(jsonPath("$.devices.length()").value(0))
    }

    // ========================================================================
    // GET /api/v1/test/device-trusts/:deviceTrustId
    // ========================================================================

    @Test
    fun `getDeviceTrust should return device trust by ID`() {
        val deviceTrustId = createDeviceTrust(testUserId, "fp_test")

        mockMvc.perform(get("/api/v1/test/device-trusts/$deviceTrustId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(deviceTrustId))
            .andExpect(jsonPath("$.userId").value(testUserId.toString()))
    }

    @Test
    fun `getDeviceTrust should return 404 when device trust not found`() {
        mockMvc.perform(get("/api/v1/test/device-trusts/nonexistent"))
            .andExpect(status().isNotFound)
    }

    // ========================================================================
    // GET /api/v1/test/device-trusts/:deviceTrustId/ttl
    // ========================================================================

    @Test
    fun `getDeviceTrustTtl should return TTL of device trust`() {
        val deviceTrustId = createDeviceTrust(testUserId, "fp_test")

        mockMvc.perform(get("/api/v1/test/device-trusts/$deviceTrustId/ttl"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ttl").exists())
    }

    @Test
    fun `getDeviceTrustTtl should return 404 when device trust not found`() {
        mockMvc.perform(get("/api/v1/test/device-trusts/nonexistent/ttl"))
            .andExpect(status().isNotFound)
    }

    // ========================================================================
    // GET /api/v1/test/events/DeviceRemembered
    // ========================================================================

    @Test
    fun `getDeviceRememberedEvents should return events for user`() {
        // Create a device trust which publishes a DeviceRemembered event
        createDeviceTrust(testUserId, "fp_test")

        // Wait a bit for async event publishing
        Thread.sleep(1000)

        mockMvc.perform(get("/api/v1/test/events/DeviceRemembered?userId=$testUserId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.events").isArray)
    }

    @Test
    fun `getDeviceRememberedEvents should return 400 when userId missing`() {
        mockMvc.perform(get("/api/v1/test/events/DeviceRemembered"))
            .andExpect(status().isBadRequest)
    }

    // ========================================================================
    // GET /api/v1/test/events/DeviceRevoked
    // ========================================================================

    @Test
    fun `getDeviceRevokedEvents should return events for user`() {
        mockMvc.perform(get("/api/v1/test/events/DeviceRevoked?userId=$testUserId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.events").isArray)
    }

    @Test
    fun `getDeviceRevokedEvents should return 400 when userId missing`() {
        mockMvc.perform(get("/api/v1/test/events/DeviceRevoked"))
            .andExpect(status().isBadRequest)
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private fun createDeviceTrust(userId: UUID, deviceFingerprint: String): String {
        val result = mockMvc.perform(
            post("/api/v1/test/users/$userId/device-trusts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "deviceFingerprint": "$deviceFingerprint",
                      "userAgent": "Mozilla/5.0 (Test)",
                      "ipAddress": "192.168.1.100"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andReturn()

        val response = objectMapper.readTree(result.response.contentAsString)
        return response.get("deviceTrustId").asText()
    }
}
