package com.acme.cart.infrastructure.persistence

import org.springframework.dao.DataIntegrityViolationException
import org.junit.jupiter.api.assertThrows
import com.acme.cart.domain.CartStatus
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

        val reloaded = carts.findBySessionIdAndStatus("sess-merge", CartStatus.ACTIVE)!!
        reloaded.addItem(variantId, 1, pricing, snapshot, 10)
        carts.saveAndFlush(reloaded)
        entityManager.clear()

        val items = carts.findBySessionIdAndStatus("sess-merge", CartStatus.ACTIVE)!!.items
        assertEquals(1, items.size)
        assertEquals(3, items.single().quantity)
    }

    @Test
    fun `removing a line deletes its row`() {
        val cart = Cart(id = UUID.randomUUID(), sessionId = "sess-remove")
        val item = cart.addItem(UUID.randomUUID(), 2, pricing, snapshot, 10).getOrNull()!!
        carts.saveAndFlush(cart)
        entityManager.clear()

        val reloaded = carts.findBySessionIdAndStatus("sess-remove", CartStatus.ACTIVE)!!
        reloaded.removeItem(item.id)
        carts.saveAndFlush(reloaded)
        entityManager.clear()

        assertEquals(0, carts.findBySessionIdAndStatus("sess-remove", CartStatus.ACTIVE)!!.items.size)
        val rows = entityManager.createNativeQuery("select count(*) from cart_items where id = :id")
            .setParameter("id", item.id)
            .singleResult as Number
        assertEquals(0L, rows.toLong())
    }

    // --- US-0004-08: ownership and status -------------------------------------------

    @Test
    fun `a session can hold only one ACTIVE cart, but a MERGED one does not count`() {
        val merged = Cart(id = UUID.randomUUID(), sessionId = "sess-owner", status = CartStatus.MERGED)
        carts.saveAndFlush(merged)
        carts.saveAndFlush(Cart(id = UUID.randomUUID(), sessionId = "sess-owner"))
        entityManager.clear()

        assertEquals(CartStatus.ACTIVE, carts.findBySessionIdAndStatus("sess-owner", CartStatus.ACTIVE)?.status)
        assertThrows<DataIntegrityViolationException> {
            carts.saveAndFlush(Cart(id = UUID.randomUUID(), sessionId = "sess-owner"))
        }
    }

    @Test
    fun `a user can hold only one ACTIVE cart`() {
        val userId = UUID.randomUUID()
        carts.saveAndFlush(Cart(id = UUID.randomUUID(), userId = userId))
        entityManager.clear()

        assertEquals(userId, carts.findByUserIdAndStatus(userId, CartStatus.ACTIVE)?.userId)
        assertThrows<DataIntegrityViolationException> {
            carts.saveAndFlush(Cart(id = UUID.randomUUID(), userId = userId))
        }
    }

    @Test
    fun `the database rejects a cart with no owner even if the entity check is bypassed`() {
        assertThrows<Exception> {
            entityManager.createNativeQuery(
                "insert into carts (id, created_at, updated_at, status) values (gen_random_uuid(), now(), now(), 'ACTIVE')"
            ).executeUpdate()
        }
    }
}
