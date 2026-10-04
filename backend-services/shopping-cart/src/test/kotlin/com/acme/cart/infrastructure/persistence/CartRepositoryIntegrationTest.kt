package com.acme.cart.infrastructure.persistence

import org.springframework.dao.DataIntegrityViolationException
import org.junit.jupiter.api.AfterEach
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
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.sql.SQLException
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

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

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
    fun `idle guest carts are found oldest first, and nothing else is`() {
        val older = guestCart("sess-older", longAgo.minus(Duration.ofDays(5)))
        older.addItem(UUID.randomUUID(), 1, pricing, snapshot, 10, now = older.lastActiveAt)
        older.addItem(UUID.randomUUID(), 1, pricing, snapshot, 10, now = older.lastActiveAt)
        carts.saveAndFlush(older)
        val old = guestCart("sess-old", longAgo)
        guestCart("sess-recent", now.minus(Duration.ofDays(1)))
        guestCart("sess-merged-old", longAgo, CartStatus.MERGED)
        carts.saveAndFlush(
            Cart(id = UUID.randomUUID(), userId = UUID.randomUUID(), createdAt = longAgo, updatedAt = longAgo, lastActiveAt = longAgo)
        )

        entityManager.clear()

        assertEquals(
            listOf(
                IdleGuestCart(older.id, "sess-older", older.lastActiveAt, lineCount = 2),
                IdleGuestCart(old.id, "sess-old", old.lastActiveAt, lineCount = 0)
            ),
            carts.findIdleGuestCarts(cutoff, PageRequest.of(0, 10))
        )
        assertEquals(listOf(older.id), carts.findIdleGuestCarts(cutoff, PageRequest.of(0, 1)).map { it.id })
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

    // --- PIN-289: final carts are deleted after the retention period -------------------------

    private val retentionCutoff = now.minus(Duration.ofDays(90))
    private val pastRetention = now.minus(Duration.ofDays(100))

    /** A cart that reached [status] at [finalizedAt], with one line so the cascade can be checked. */
    private fun finalCart(session: String, status: CartStatus, finalizedAt: Instant): Cart {
        val cart = Cart(
            id = UUID.randomUUID(), sessionId = session, status = status,
            createdAt = finalizedAt, updatedAt = finalizedAt, lastActiveAt = finalizedAt
        )
        cart.addItem(UUID.randomUUID(), 1, pricing, snapshot, 10, now = finalizedAt)
        return carts.saveAndFlush(cart)
    }

    private fun rowCount(sql: String, id: UUID): Long =
        (entityManager.createNativeQuery(sql).setParameter("id", id).singleResult as Number).toLong()

    @Test
    fun `final carts past retention are found oldest first, and nothing else is`() {
        val older = finalCart("sess-purge-older", CartStatus.MERGED, pastRetention.minus(Duration.ofDays(5)))
        val old = finalCart("sess-purge-old", CartStatus.EXPIRED, pastRetention)
        finalCart("sess-purge-young", CartStatus.EXPIRED, now.minus(Duration.ofDays(10)))
        guestCart("sess-purge-active", pastRetention)
        carts.saveAndFlush(
            Cart(id = UUID.randomUUID(), userId = UUID.randomUUID(), createdAt = pastRetention, updatedAt = pastRetention, lastActiveAt = pastRetention)
        )
        entityManager.clear()

        assertEquals(
            listOf(
                FinalCart(older.id, CartStatus.MERGED, older.updatedAt),
                FinalCart(old.id, CartStatus.EXPIRED, old.updatedAt)
            ),
            carts.findFinalCartsBefore(retentionCutoff, PageRequest.of(0, 10))
        )
        assertEquals(listOf(older.id), carts.findFinalCartsBefore(retentionCutoff, PageRequest.of(0, 1)).map { it.id })
    }

    @Test
    fun `a final cart past retention is deleted with its lines, once`() {
        val cart = finalCart("sess-purge-delete", CartStatus.EXPIRED, pastRetention)

        assertEquals(1, carts.deleteIfFinalBefore(cart.id, retentionCutoff))
        assertEquals(0, rowCount("select count(*) from carts where id = :id", cart.id))
        assertEquals(0, rowCount("select count(*) from cart_items where cart_id = :id", cart.id))
        assertEquals(0, carts.deleteIfFinalBefore(cart.id, retentionCutoff), "a deleted cart is not deleted again")
    }

    @Test
    fun `active carts of any age and final carts within retention are never deleted`() {
        val activeGuest = guestCart("sess-purge-keep-active", pastRetention)
        val activeUser = carts.saveAndFlush(
            Cart(id = UUID.randomUUID(), userId = UUID.randomUUID(), createdAt = pastRetention, updatedAt = pastRetention, lastActiveAt = pastRetention)
        )
        val young = finalCart("sess-purge-keep-young", CartStatus.MERGED, now.minus(Duration.ofDays(10)))

        listOf(activeGuest, activeUser, young).forEach {
            assertEquals(0, carts.deleteIfFinalBefore(it.id, retentionCutoff))
            assertEquals(1, rowCount("select count(*) from carts where id = :id", it.id))
        }
    }

    // --- PIN-278: optimistic locking on the cart ---------------------------------------------
    // These tests run outside the test-managed transaction, so each block below commits for
    // real. Only then can two transactions disagree about a cart's version.

    private fun inTransaction() = TransactionTemplate(transactionManager)

    /** Commits on its own, even when called inside [inTransaction]: the concurrent request. */
    private fun concurrently() = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    private fun versionOf(id: UUID): Long =
        (entityManager.createNativeQuery("select version from carts where id = :id")
            .setParameter("id", id).singleResult as Number).toLong()

    /**
     * What these tests committed must not leak into the other tests, which only roll back. Its
     * own transaction, so it also runs after a test whose transaction a violation aborted.
     */
    @AfterEach
    fun deleteCommittedCarts() {
        concurrently().execute {
            entityManager.createNativeQuery("delete from carts where session_id like 'sess-version-%'").executeUpdate()
        }
    }

    /** Hibernate inserts the line before it checks the version: `retryOnConflict` relies on 23505. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `two adds of the same new variant collide on the line's unique key, before the version check`() {
        carts.saveAndFlush(Cart(id = UUID.randomUUID(), sessionId = "sess-version-same-variant"))
        val variantId = UUID.randomUUID()

        val collision = assertThrows<DataIntegrityViolationException> {
            inTransaction().execute {
                val mine = carts.findBySessionIdAndStatus("sess-version-same-variant", CartStatus.ACTIVE)!!
                concurrently().execute {
                    val theirs = carts.findBySessionIdAndStatus("sess-version-same-variant", CartStatus.ACTIVE)!!
                    theirs.addItem(variantId, 1, pricing, snapshot, 10)
                    carts.save(theirs)
                }
                mine.addItem(variantId, 1, pricing, snapshot, 10)
                carts.save(mine)
            }
        }
        assertEquals("23505", (collision.mostSpecificCause as SQLException).sqlState)
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `a new cart is saved as a new row at version 0`() {
        val cart = carts.saveAndFlush(Cart(id = UUID.randomUUID(), sessionId = "sess-version-new"))

        assertEquals(0L, versionOf(cart.id))
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `an update that loaded the cart before a concurrent remove is rejected, not a 500`() {
        val cart = Cart(id = UUID.randomUUID(), sessionId = "sess-version-race")
        val line = cart.addItem(UUID.randomUUID(), 2, pricing, snapshot, 10).getOrNull()!!
        carts.saveAndFlush(cart)

        assertThrows<ObjectOptimisticLockingFailureException> {
            inTransaction().execute {
                val mine = carts.findBySessionIdAndStatus("sess-version-race", CartStatus.ACTIVE)!!
                concurrently().execute {
                    val theirs = carts.findBySessionIdAndStatus("sess-version-race", CartStatus.ACTIVE)!!
                    theirs.removeItem(line.id)
                    carts.save(theirs)
                }
                mine.updateItemQuantity(line.id, 3, pricing, 10)
                carts.save(mine)
            }
        }
        assertEquals(0, rowCount("select count(*) from cart_items where cart_id = :id", cart.id))
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `an add that loaded the cart before the expiry job expired it cannot revive it`() {
        val idle = guestCart("sess-version-expired", longAgo)

        assertThrows<ObjectOptimisticLockingFailureException> {
            inTransaction().execute {
                val mine = carts.findBySessionIdAndStatus("sess-version-expired", CartStatus.ACTIVE)!!
                concurrently().execute { carts.expireIfIdle(idle.id, cutoff, now) }
                mine.addItem(UUID.randomUUID(), 1, pricing, snapshot, 10)
                carts.save(mine)
            }
        }
        entityManager.clear()
        assertEquals(CartStatus.EXPIRED, carts.findById(idle.id).get().status)
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `the activity touch does not change the version, so a view never conflicts with a change`() {
        val cart = guestCart("sess-version-touch", now.minus(Duration.ofDays(2)))
        val before = versionOf(cart.id)

        assertEquals(1, carts.touchGuestCart("sess-version-touch", now, now.minus(Duration.ofDays(1))))

        assertEquals(before, versionOf(cart.id))
    }

    // --- PIN-329: a cart in checkout is still the current cart, and is left alone ------

    /** A guest cart last active at [lastActive], with one line of 2 units, locked for checkout then. */
    private fun lockedGuestCart(session: String, lastActive: Instant): Cart {
        val cart = Cart(
            id = UUID.randomUUID(), sessionId = session,
            createdAt = lastActive, updatedAt = lastActive, lastActiveAt = lastActive
        )
        cart.addItem(UUID.randomUUID(), 2, pricing, snapshot, 10, now = lastActive)
        cart.startCheckout(Duration.ofMinutes(30), now = lastActive)
        return carts.saveAndFlush(cart)
    }

    @Test
    fun `a cart in checkout is the session's current cart and blocks a second one`() {
        val locked = lockedGuestCart("sess-checkout", now)
        entityManager.clear()

        val found = carts.findBySessionIdAndStatusIn("sess-checkout", CartStatus.CURRENT)
        assertEquals(locked.id, found?.id)
        assertEquals(CartStatus.CHECKOUT, found?.status)
        assertEquals(locked.checkoutSessionId, found?.checkoutSessionId)
        assertEquals(locked.checkoutExpiresAt, found?.checkoutExpiresAt)
        assertThrows<DataIntegrityViolationException> {
            carts.saveAndFlush(Cart(id = UUID.randomUUID(), sessionId = "sess-checkout"))
        }
    }

    @Test
    fun `a user's cart in checkout blocks a second current cart`() {
        val userId = UUID.randomUUID()
        val cart = Cart(id = UUID.randomUUID(), userId = userId)
        cart.addItem(UUID.randomUUID(), 1, pricing, snapshot, 10)
        cart.startCheckout(Duration.ofMinutes(30))
        carts.saveAndFlush(cart)
        entityManager.clear()

        assertEquals(cart.id, carts.findByUserIdAndStatusIn(userId, CartStatus.CURRENT)?.id)
        assertThrows<DataIntegrityViolationException> {
            carts.saveAndFlush(Cart(id = UUID.randomUUID(), userId = userId))
        }
    }

    @Test
    fun `an idle cart in checkout is neither expired, touched nor purged`() {
        val locked = lockedGuestCart("sess-checkout-idle", pastRetention)
        entityManager.clear()

        assertEquals(emptyList(), carts.findIdleGuestCarts(cutoff, PageRequest.of(0, 10)).filter { it.id == locked.id })
        assertEquals(0, carts.expireIfIdle(locked.id, cutoff, now))
        assertEquals(0, carts.touchGuestCart("sess-checkout-idle", now, now.minus(Duration.ofDays(1))))
        assertEquals(emptyList(), carts.findFinalCartsBefore(retentionCutoff, PageRequest.of(0, 10)).filter { it.id == locked.id })
        assertEquals(0, carts.deleteIfFinalBefore(locked.id, retentionCutoff))
        assertEquals(CartStatus.CHECKOUT, statusOf(locked))
    }

    // --- PIN-330: lapsed checkout sessions unlock -----------------------------------------

    private fun lastActiveOf(cart: Cart): Instant {
        entityManager.clear()
        return carts.findById(cart.id).get().lastActiveAt
    }

    @Test
    fun `a lapsed checkout is found and unlocked once, keeping its lines and activity`() {
        val lapsed = lockedGuestCart("sess-checkout-lapsed", now.minus(Duration.ofHours(1)))
        entityManager.clear()

        val found = carts.findLapsedCheckouts(now, PageRequest.of(0, 10)).single { it.id == lapsed.id }
        assertEquals(lapsed.checkoutSessionId, found.checkoutSessionId)
        assertEquals(lapsed.checkoutExpiresAt, found.expiredAt)
        assertEquals("sess-checkout-lapsed", found.sessionId)
        assertEquals(1, found.lineCount)
        assertEquals(2L, found.itemCount)

        assertEquals(1, carts.unlockIfLapsed(lapsed.id, now))
        assertEquals(0, carts.unlockIfLapsed(lapsed.id, now), "an unlocked cart is not unlocked again")
        entityManager.clear()
        val unlocked = carts.findById(lapsed.id).get()
        assertEquals(CartStatus.ACTIVE, unlocked.status)
        assertEquals(null, unlocked.checkoutSessionId)
        assertEquals(null, unlocked.checkoutExpiresAt)
        assertEquals(1, unlocked.items.size)
        assertEquals(now, unlocked.updatedAt)
        assertEquals(lapsed.lastActiveAt, unlocked.lastActiveAt)
    }

    @Test
    fun `a live checkout is neither found nor unlocked`() {
        val live = lockedGuestCart("sess-checkout-live", now)
        entityManager.clear()

        assertEquals(emptyList(), carts.findLapsedCheckouts(now, PageRequest.of(0, 10)).filter { it.id == live.id })
        assertEquals(0, carts.unlockIfLapsed(live.id, now))
        assertEquals(CartStatus.CHECKOUT, statusOf(live))
    }

    @Test
    fun `a lapsed idle guest checkout unlocks, then expires like any idle guest cart`() {
        val stale = lockedGuestCart("sess-checkout-then-expire", longAgo)

        assertEquals(1, carts.unlockIfLapsed(stale.id, now))
        assertEquals(longAgo, lastActiveOf(stale))
        assertEquals(1, carts.expireIfIdle(stale.id, cutoff, now))
        assertEquals(CartStatus.EXPIRED, statusOf(stale))
    }

    /**
     * The guest cart stranded by sign-in: merge leaves a guest cart in checkout alone
     * (MergeCartsUseCaseTest), so while the user shops on their own cart the guest one stays
     * locked. Once its session lapses it is unlocked, and the guest session can use it again.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `a guest cart left in checkout by signing in is unlocked and usable again by its session`() {
        val guest = lockedGuestCart("sess-version-signin", now.minus(Duration.ofHours(1)))

        assertEquals(1, carts.unlockIfLapsed(guest.id, now))

        inTransaction().execute {
            val mine = carts.findBySessionIdAndStatusIn("sess-version-signin", CartStatus.CURRENT)!!
            assertEquals(guest.id, mine.id)
            assertEquals(CartStatus.ACTIVE, mine.status)
            mine.addItem(UUID.randomUUID(), 1, pricing, snapshot, 10)
            carts.save(mine)
        }
        assertEquals(2L, rowCount("select count(*) from cart_items where cart_id = :id", guest.id))
    }
}
