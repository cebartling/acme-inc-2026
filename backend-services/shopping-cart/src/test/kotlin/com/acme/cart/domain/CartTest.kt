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
}
