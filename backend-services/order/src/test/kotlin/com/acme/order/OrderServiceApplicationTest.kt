package com.acme.order

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Boots the whole service against a real Postgres, so configuration that only fails at
 * startup fails CI (the gap PIN-276 closed for customer and notification).
 */
@SpringBootTest
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

    @Test
    fun `the context starts and Flyway applies every migration`() {
        assertEquals("5", flyway.info().current()?.version?.version)
    }
}
