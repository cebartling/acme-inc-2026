package com.acme.cart.infrastructure.persistence

import com.acme.cart.domain.Cart
import com.acme.cart.domain.VariantPricing
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Exercises the add-to-cart persistence path against real Postgres: a cart reloaded in a
 * later transaction must merge a repeat add into its existing row, which the
 * `uq_cart_items_cart_variant` constraint would otherwise reject.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CartRepositoryIntegrationTest {

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

    @Autowired
    private lateinit var carts: CartRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private val pricing = VariantPricing(BigDecimal("69.99"))
    private val snapshot = """{"name":"ACME Gaming Mouse Pro"}"""

    @Test
    fun `a repeat add to a reloaded cart updates the existing row`() {
        val variantId = UUID.randomUUID()
        val cart = Cart(id = UUID.randomUUID(), sessionId = "sess-merge")
        cart.addItem(variantId, 2, pricing, snapshot, 10)
        carts.saveAndFlush(cart)
        entityManager.clear()

        val reloaded = carts.findBySessionId("sess-merge")!!
        reloaded.addItem(variantId, 1, pricing, snapshot, 10)
        carts.saveAndFlush(reloaded)
        entityManager.clear()

        val items = carts.findBySessionId("sess-merge")!!.items
        assertEquals(1, items.size)
        assertEquals(3, items.single().quantity)
    }
}
