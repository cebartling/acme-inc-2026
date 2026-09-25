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
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
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

    // --- US-0004-08: merge persistence ------------------------------------------------

    @Test
    fun `a merge saves both carts and frees the session for a new guest cart`() {
        val variantId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val guest = Cart(id = UUID.randomUUID(), sessionId = "sess-merge-db")
        guest.addItem(variantId, 2, pricing, snapshot, 10)
        val user = Cart(id = UUID.randomUUID(), userId = userId)
        user.addItem(variantId, 1, pricing, snapshot, 10)
        carts.saveAndFlush(guest)
        carts.saveAndFlush(user)
        entityManager.clear()

        val reloadedGuest = carts.findForUpdateBySessionIdAndStatus("sess-merge-db", CartStatus.ACTIVE)!!
        val reloadedUser = carts.findByUserIdAndStatus(userId, CartStatus.ACTIVE)!!
        reloadedUser.absorb(reloadedGuest, mapOf(variantId to pricing), maxQuantity = 10)
        carts.save(reloadedGuest)
        carts.saveAndFlush(reloadedUser)
        entityManager.clear()

        assertEquals(3, carts.findByUserIdAndStatus(userId, CartStatus.ACTIVE)!!.items.single().quantity)
        assertEquals(null, carts.findBySessionIdAndStatus("sess-merge-db", CartStatus.ACTIVE))
        assertEquals(CartStatus.MERGED, carts.findById(guest.id).get().status)
        // The session can start over as a guest after sign-out.
        carts.saveAndFlush(Cart(id = UUID.randomUUID(), sessionId = "sess-merge-db"))
    }

    // --- PIN-287: idle guest carts expire -----------------------------------------------

    private val now = Instant.parse("2026-09-24T12:00:00Z")
    private val cutoff = now.minus(Duration.ofDays(31))
    private val longAgo = now.minus(Duration.ofDays(40))

    private fun guestCart(session: String, lastActive: Instant, status: CartStatus = CartStatus.ACTIVE) =
        carts.saveAndFlush(
            Cart(
                id = UUID.randomUUID(),
                sessionId = session,
                status = status,
                createdAt = lastActive,
                updatedAt = lastActive,
                lastActiveAt = lastActive
            )
        )

    private fun statusOf(cart: Cart): CartStatus {
        entityManager.clear()
        return carts.findById(cart.id).get().status
    }

    @Test
    fun `an idle ACTIVE guest cart expires, once`() {
        val idle = guestCart("sess-idle", longAgo)

        assertEquals(1, carts.expireIfIdle(idle.id, cutoff, now))
        assertEquals(CartStatus.EXPIRED, statusOf(idle))
        assertEquals(0, carts.expireIfIdle(idle.id, cutoff, now), "an expired cart is not expired again")
    }

    @Test
    fun `a recently active, merged or user cart never expires`() {
        val fresh = guestCart("sess-fresh", now.minus(Duration.ofDays(2)))
        val merged = guestCart("sess-merged", longAgo, CartStatus.MERGED)
        val user = carts.saveAndFlush(
            Cart(id = UUID.randomUUID(), userId = UUID.randomUUID(), createdAt = longAgo, updatedAt = longAgo, lastActiveAt = longAgo)
        )

        listOf(fresh, merged, user).forEach { assertEquals(0, carts.expireIfIdle(it.id, cutoff, now)) }

        assertEquals(CartStatus.ACTIVE, statusOf(fresh))
        assertEquals(CartStatus.MERGED, statusOf(merged))
        assertEquals(CartStatus.ACTIVE, statusOf(user))
    }

    @Test
    fun `idle guest cart ids are found oldest first, and nothing else is`() {
        val older = guestCart("sess-older", longAgo.minus(Duration.ofDays(5)))
        val old = guestCart("sess-old", longAgo)
        guestCart("sess-recent", now.minus(Duration.ofDays(1)))
        guestCart("sess-merged-old", longAgo, CartStatus.MERGED)
        carts.saveAndFlush(
            Cart(id = UUID.randomUUID(), userId = UUID.randomUUID(), createdAt = longAgo, updatedAt = longAgo, lastActiveAt = longAgo)
        )

        assertEquals(listOf(older.id, old.id), carts.findIdleGuestCartIds(cutoff, PageRequest.of(0, 10)))
        assertEquals(listOf(older.id), carts.findIdleGuestCartIds(cutoff, PageRequest.of(0, 1)))
    }

    @Test
    fun `an expired cart frees its session for a new guest cart`() {
        val idle = guestCart("sess-expired", longAgo)
        carts.expireIfIdle(idle.id, cutoff, now)
        entityManager.clear()

        assertEquals(null, carts.findBySessionIdAndStatus("sess-expired", CartStatus.ACTIVE))
        carts.saveAndFlush(Cart(id = UUID.randomUUID(), sessionId = "sess-expired"))
    }

    @Test
    fun `viewing marks a cart active only when its activity is stale`() {
        val lastActive = now.minus(Duration.ofHours(30))
        val cart = guestCart("sess-touch", lastActive)
        val staleBefore = now.minus(Duration.ofDays(1))

        assertEquals(1, carts.touchGuestCart("sess-touch", now, staleBefore))
        entityManager.clear()
        assertEquals(now, carts.findById(cart.id).get().lastActiveAt)

        assertEquals(0, carts.touchGuestCart("sess-touch", now.plusSeconds(60), staleBefore), "throttled to once a day")
        assertEquals(0, carts.touchGuestCart("sess-unknown", now, staleBefore))
    }

    @Test
    fun `viewing never revives an expired cart`() {
        val idle = guestCart("sess-dead", longAgo)
        carts.expireIfIdle(idle.id, cutoff, now)

        assertEquals(0, carts.touchGuestCart("sess-dead", now, now.minus(Duration.ofDays(1))))
    }
}
