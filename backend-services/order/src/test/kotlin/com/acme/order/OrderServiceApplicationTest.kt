package com.acme.order

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Boots the whole service against a real Postgres, so configuration that only fails at
 * startup fails CI (the gap PIN-276 closed for customer and notification).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OrderServiceApplicationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("acme_orders_test")
            .withUsername("test")
            .withPassword("test")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("acme.cart.service-token") { "test-order-service-token" }
        }
    }

    @Autowired
    private lateinit var flyway: Flyway

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `the context starts and Flyway applies every migration`() {
        assertEquals("6", flyway.info().current()?.version?.version)
    }

    /** Prometheus scrapes this; the job counters must be in it (PIN-493). */
    @Test
    fun `the prometheus endpoint exposes JVM metrics and the order counters`() {
        val body = mockMvc.get("/actuator/prometheus")
            .andExpect { status { isOk() } }
            .andReturn().response.contentAsString

        assertTrue(body.contains("jvm_memory_used_bytes"), "JVM metrics are missing")
        assertTrue(body.contains("orders_cancelled_total"), "orders_cancelled_total is missing")
        assertTrue(body.contains("orders_recovered_total"), "orders_recovered_total is missing")
    }
}
