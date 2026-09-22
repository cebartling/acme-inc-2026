package com.acme.cart.infrastructure.persistence

import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartItem
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.math.BigDecimal
import java.util.UUID

/**
 * Guards against divergence between this service's `@Entity` mappings and its Flyway
 * migrations, following customer's SchemaValidationTest (PIN-276).
 *
 * Reaching the test body means Flyway applied every migration and Hibernate's
 * `ddl-auto: validate` accepted every entity. `validate` checks table and column
 * existence and type only, so the round trip below also exercises the JSONB snapshot
 * column and the cart-to-items cascade.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class SchemaValidationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("acme_carts_test")
            .withUsername("test")
            .withPassword("test")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }

    @Value("\${spring.jpa.hibernate.ddl-auto}")
    private lateinit var ddlAuto: String

    @Autowired
    private lateinit var carts: CartRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `every entity validates against the Flyway-migrated schema`() {
        assertEquals(
            "validate",
            ddlAuto,
            "this test only guards against schema drift while ddl-auto is validate"
        )

        val cart = Cart(id = UUID.randomUUID(), sessionId = "sess-schema-test")
        cart.items += CartItem(
            id = UUID.randomUUID(),
            cart = cart,
            variantId = UUID.randomUUID(),
            quantity = 2,
            unitPrice = BigDecimal("69.99"),
            productSnapshot = """{"name":"ACME Gaming Mouse Pro","sku":"ACME-GM-PRO-BLK"}"""
        )
        carts.saveAndFlush(cart)
        // Force a real read from Postgres rather than the persistence-context cache.
        entityManager.clear()

        val found = carts.findBySessionId("sess-schema-test")
        assertEquals(1, found?.items?.size)
        assertEquals(BigDecimal("69.99"), found?.items?.first()?.unitPrice)
    }
}
