package com.acme.cart.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CartTest {

    private val variantId = UUID.randomUUID()
    private val basePricing = VariantPricing(price = BigDecimal("69.99"))
    private val tieredPricing = VariantPricing(
        price = BigDecimal("69.99"),
        tiers = listOf(PriceTier(3, BigDecimal("64.99")), PriceTier(10, BigDecimal("59.99")))
    )

    private fun newCart() = Cart(id = UUID.randomUUID(), sessionId = "sess-1")

    private fun Cart.add(quantity: Int, pricing: VariantPricing = basePricing, max: Int = 10) =
        addItem(variantId, quantity, pricing, """{"name":"Mouse"}""", max)

    @Test
    fun `adding a new variant creates one line at the base price`() {
        val cart = newCart()

        val item = cart.add(2).getOrNull()!!

        assertEquals(listOf(item), cart.items)
        assertEquals(2, item.quantity)
        assertEquals(BigDecimal("69.99"), item.unitPrice)
        assertEquals(BigDecimal("139.98"), item.lineTotal)
        assertEquals(2, cart.itemCount)
    }

    @Test
    fun `adding the same variant again increments the existing line`() {
        val cart = newCart()
        val first = cart.add(2).getOrNull()!!

        val second = cart.add(1).getOrNull()!!

        assertSame(first, second)
        assertEquals(1, cart.items.size)
        assertEquals(3, second.quantity)
    }

    @Test
    fun `crossing a tier threshold reprices the whole line`() {
        val cart = newCart()
        cart.add(2, tieredPricing)

        val item = cart.add(1, tieredPricing).getOrNull()!!

        assertEquals(BigDecimal("64.99"), item.unitPrice)
        assertEquals(BigDecimal("194.97"), item.lineTotal)
    }

    @Test
    fun `exceeding the max quantity is refused and leaves the cart unchanged`() {
        val cart = newCart()
        cart.add(4, max = 5)

        val result = cart.add(2, max = 5)

        assertEquals(CartError.MaxQuantityExceeded(5), result.leftOrNull())
        assertEquals("Maximum order quantity is 5 for this item", result.leftOrNull()!!.message)
        assertEquals(4, cart.items.single().quantity)
    }

    @Test
    fun `reaching exactly the max quantity is allowed`() {
        val cart = newCart()

        assertTrue(cart.add(5, max = 5).isRight())
    }

    @Test
    fun `a non-positive quantity is a programming error`() {
        assertThrows<IllegalArgumentException> { newCart().add(0) }
    }

    @Test
    fun `updating a quantity reprices the line, down a tier as well as up`() {
        val cart = newCart()
        val item = cart.add(3, tieredPricing).getOrNull()!!

        val change = cart.updateItemQuantity(item.id, 2, tieredPricing, 10).getOrNull()!!

        assertEquals(3, change.previousQuantity)
        assertEquals(2, item.quantity)
        assertEquals(BigDecimal("69.99"), item.unitPrice)
    }

    @Test
    fun `updating past the max is refused and leaves the line unchanged`() {
        val cart = newCart()
        val item = cart.add(2).getOrNull()!!

        assertEquals(CartError.MaxQuantityExceeded(5), cart.updateItemQuantity(item.id, 6, basePricing, 5).leftOrNull())
        assertTrue(cart.updateItemQuantity(item.id, 5, basePricing, 5).isRight())
        assertEquals(5, item.quantity)
    }

    @Test
    fun `updating or removing an item that is not in the cart is CartItemNotFound`() {
        val cart = newCart()
        val missing = UUID.randomUUID()

        assertEquals(CartError.CartItemNotFound(missing), cart.updateItemQuantity(missing, 1, basePricing, 10).leftOrNull())
        assertEquals(CartError.CartItemNotFound(missing), cart.removeItem(missing).leftOrNull())
    }

    @Test
    fun `removing the last line leaves an empty cart`() {
        val cart = newCart()
        val item = cart.add(2).getOrNull()!!

        assertSame(item, cart.removeItem(item.id).getOrNull())
        assertEquals(emptyList(), cart.items)
        assertEquals(0, cart.itemCount)
    }

    // --- PIN-294: clear the entire cart -------------------------------------------------

    @Test
    fun `clearing removes every line and keeps the cart`() {
        val cart = newCart()
        val first = cart.add(2).getOrNull()!!
        val second = cart.addItem(UUID.randomUUID(), 1, basePricing, """{"name":"Pad"}""", 10).getOrNull()!!
        val cleared = java.time.Instant.parse("2026-09-01T00:00:00Z")

        val removed = cart.clear(now = cleared).getOrNull()!!

        assertEquals(listOf(first, second), removed)
        assertEquals(emptyList(), cart.items)
        assertEquals(0, cart.itemCount)
        assertEquals(CartStatus.ACTIVE, cart.status)
        assertEquals(cleared, cart.lastActiveAt)
        assertEquals(cleared, cart.updatedAt)
    }

    @Test
    fun `clearing an empty cart changes nothing`() {
        val cart = newCart()
        val before = cart.updatedAt

        val removed = cart.clear(now = before.plusSeconds(60)).getOrNull()!!

        assertEquals(emptyList(), removed)
        assertEquals(before, cart.updatedAt)
        assertEquals(before, cart.lastActiveAt)
    }

    @Test
    fun `a cart belongs to exactly one of a session or a user`() {
        assertThrows<IllegalArgumentException> { Cart(id = UUID.randomUUID()) }
        assertThrows<IllegalArgumentException> {
            Cart(id = UUID.randomUUID(), sessionId = "sess-1", userId = UUID.randomUUID())
        }
    }

    @Test
    fun `newCartFor builds an ACTIVE cart for either kind of owner`() {
        val userId = UUID.randomUUID()

        val guest = newCartFor(CartOwner.Guest("sess-9"))
        val customer = newCartFor(CartOwner.Customer(userId))

        assertEquals("sess-9", guest.sessionId)
        assertEquals(null, guest.userId)
        assertEquals(userId, customer.userId)
        assertEquals(null, customer.sessionId)
        assertEquals(CartStatus.ACTIVE, customer.status)
    }

    // --- PIN-287: activity for idle-cart expiry -----------------------------------------

    @Test
    fun `a new cart is active as of its creation`() {
        val cart = newCart()

        assertEquals(cart.createdAt, cart.lastActiveAt)
    }

    @Test
    fun `every change marks the cart active`() {
        val cart = newCart()
        val added = java.time.Instant.parse("2026-09-01T00:00:00Z")
        val item = cart.addItem(variantId, 1, basePricing, """{"name":"Mouse"}""", 10, now = added).getOrNull()!!
        assertEquals(added, cart.lastActiveAt)

        val updated = added.plusSeconds(60)
        cart.updateItemQuantity(item.id, 2, basePricing, 10, now = updated)
        assertEquals(updated, cart.lastActiveAt)

        val removed = updated.plusSeconds(60)
        cart.removeItem(item.id, now = removed)
        assertEquals(removed, cart.lastActiveAt)
    }

    @Test
    fun `a merge marks the account cart active`() {
        val guest = newCart()
        guest.add(1)
        val user = Cart(id = UUID.randomUUID(), userId = UUID.randomUUID())
        val merged = java.time.Instant.parse("2026-09-01T00:00:00Z")

        user.absorb(guest, mapOf(variantId to basePricing), maxQuantity = 10, now = merged)

        assertEquals(merged, user.lastActiveAt)
    }

    // --- PIN-329: starting checkout locks the cart ---------------------------------------

    private val sessionLength = java.time.Duration.ofMinutes(30)

    @Test
    fun `starting checkout locks the cart for a new session`() {
        val cart = newCart()
        cart.add(2)
        val started = java.time.Instant.parse("2026-09-01T00:00:00Z")

        val session = cart.startCheckout(sessionLength, now = started).getOrNull()!!

        assertEquals(CartStatus.CHECKOUT, cart.status)
        assertEquals(cart.checkoutSessionId, session.id)
        assertEquals(started.plus(sessionLength), session.expiresAt)
        assertEquals(session.expiresAt, cart.checkoutExpiresAt)
        assertEquals(started, cart.updatedAt)
    }

    @Test
    fun `an empty cart cannot start checkout`() {
        val cart = newCart()

        assertEquals(CartError.CartEmpty(cart.id), cart.startCheckout(sessionLength).leftOrNull())
        assertEquals(CartStatus.ACTIVE, cart.status)
        assertEquals(null, cart.checkoutSessionId)
    }

    @Test
    fun `starting checkout again resumes the same session and extends it`() {
        val cart = newCart()
        cart.add(1)
        val first = cart.startCheckout(sessionLength).getOrNull()!!
        val resumed = cart.updatedAt.plusSeconds(60)

        val second = cart.startCheckout(sessionLength, now = resumed).getOrNull()!!

        assertEquals(first.id, second.id)
        assertEquals(resumed.plus(sessionLength), second.expiresAt)
        assertEquals(second.expiresAt, cart.checkoutExpiresAt)
        assertEquals(resumed, cart.lastActiveAt)
    }

    // --- PIN-330: lapsed sessions and leaving checkout -----------------------------------

    @Test
    fun `starting checkout after the session lapsed starts a new session`() {
        val cart = newCart()
        cart.add(1)
        val first = cart.startCheckout(sessionLength).getOrNull()!!
        val lapsed = first.expiresAt.plusSeconds(1)

        assertEquals(false, cart.isInLiveCheckout(lapsed))
        assertEquals(first, cart.lapsedCheckoutSession(lapsed))
        assertEquals(null, cart.lapsedCheckoutSession(first.expiresAt.minusSeconds(1)))
        val second = cart.startCheckout(sessionLength, now = lapsed).getOrNull()!!

        assertTrue(first.id != second.id)
        assertEquals(lapsed.plus(sessionLength), second.expiresAt)
        assertEquals(CartStatus.CHECKOUT, cart.status)
    }

    @Test
    fun `abandoning checkout unlocks the cart and keeps its lines`() {
        val cart = newCart()
        val item = cart.add(2).getOrNull()!!
        val session = cart.startCheckout(sessionLength).getOrNull()!!
        val left = session.expiresAt.minusSeconds(60)

        assertEquals(session, cart.abandonCheckout(now = left))

        assertEquals(CartStatus.ACTIVE, cart.status)
        assertEquals(null, cart.checkoutSessionId)
        assertEquals(null, cart.checkoutExpiresAt)
        assertEquals(listOf(item), cart.items)
        assertEquals(left, cart.updatedAt)
        assertTrue(cart.add(1).isRight())
    }

    @Test
    fun `abandoning a cart that is not in checkout changes nothing`() {
        val cart = newCart()
        cart.add(1)
        val updated = cart.updatedAt

        assertEquals(null, cart.abandonCheckout(now = updated.plusSeconds(60)))
        assertEquals(CartStatus.ACTIVE, cart.status)
        assertEquals(updated, cart.updatedAt)
    }

    @Test
    fun `a cart in checkout refuses every change`() {
        val cart = newCart()
        val item = cart.add(2).getOrNull()!!
        cart.startCheckout(sessionLength)
        val locked = CartError.CartLocked(cart.id)

        assertEquals(locked, cart.add(1).leftOrNull())
        assertEquals(locked, cart.updateItemQuantity(item.id, 3, basePricing, 10).leftOrNull())
        assertEquals(locked, cart.removeItem(item.id).leftOrNull())
        assertEquals(locked, cart.clear().leftOrNull())
        assertEquals(listOf(item), cart.items)
        assertEquals(2, item.quantity)
    }

    @Test
    fun `a cart in checkout cannot absorb a guest cart`() {
        val guest = newCart()
        guest.add(1)
        val user = Cart(id = UUID.randomUUID(), userId = UUID.randomUUID())
        user.addItem(UUID.randomUUID(), 1, basePricing, """{"name":"Pad"}""", 10)
        user.startCheckout(sessionLength)

        assertThrows<IllegalArgumentException> {
            user.absorb(guest, mapOf(variantId to basePricing), maxQuantity = 10)
        }
        assertEquals(CartStatus.ACTIVE, guest.status)
    }
}
