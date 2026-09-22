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
}
