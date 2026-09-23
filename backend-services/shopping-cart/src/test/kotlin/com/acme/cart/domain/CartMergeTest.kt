package com.acme.cart.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals

class CartMergeTest {

    private val mouse = UUID.randomUUID()
    private val keyboard = UUID.randomUUID()
    private val tiered = VariantPricing(BigDecimal("69.99"), listOf(PriceTier(3, BigDecimal("64.99"))))
    private val flat = VariantPricing(BigDecimal("129.00"))
    private val pricing = mapOf(mouse to tiered, keyboard to flat)

    private fun userCart() = newCartFor(CartOwner.Customer(UUID.randomUUID()))
    private fun guestCart() = newCartFor(CartOwner.Guest("sess-guest"))
    private fun Cart.with(variant: UUID, quantity: Int, snapshot: String = """{"v":"$variant"}""") =
        apply { addItem(variant, quantity, pricing.getValue(variant), snapshot, 10) }

    @Test
    fun `lines in both carts are summed and repriced at the new quantity (AC-03)`() {
        val user = userCart().with(mouse, 2)
        val guest = guestCart().with(mouse, 1)

        val result = user.absorb(guest, pricing, maxQuantity = 10)

        val line = user.items.single()
        assertEquals(3, line.quantity)
        assertEquals(BigDecimal("64.99"), line.unitPrice)
        assertEquals(MergeResult(itemsMerged = 1, quantitiesAdjusted = emptyList()), result)
    }

    @Test
    fun `the account cart's own lines are kept and guest-only lines are copied with their snapshot (AC-02)`() {
        val user = userCart().with(keyboard, 1)
        val guest = guestCart().with(mouse, 2, snapshot = """{"name":"Mouse"}""")

        user.absorb(guest, pricing, maxQuantity = 10)

        assertEquals(setOf(keyboard, mouse), user.items.map { it.variantId }.toSet())
        val copied = user.items.single { it.variantId == mouse }
        assertEquals(2, copied.quantity)
        assertEquals("""{"name":"Mouse"}""", copied.productSnapshot)
        assertEquals(user, copied.cart)
    }

    @Test
    fun `a summed quantity over the max is capped and reported (AC-04)`() {
        val user = userCart().with(mouse, 3)
        val guest = guestCart().with(mouse, 4)

        val result = user.absorb(guest, pricing, maxQuantity = 5)

        assertEquals(5, user.items.single().quantity)
        assertEquals(listOf(QuantityAdjustment(mouse, requestedTotal = 7, adjustedTo = 5)), result.quantitiesAdjusted)
    }

    @Test
    fun `the guest cart is marked MERGED and cannot be merged again (AC-05)`() {
        val user = userCart()
        val guest = guestCart().with(mouse, 1)

        user.absorb(guest, pricing, maxQuantity = 10)

        assertEquals(CartStatus.MERGED, guest.status)
        assertThrows<IllegalArgumentException> { userCart().absorb(guest, pricing, maxQuantity = 10) }
    }

    @Test
    fun `merging without pricing for a guest variant is a programming error`() {
        assertThrows<IllegalArgumentException> {
            userCart().absorb(guestCart().with(mouse, 1), emptyMap(), maxQuantity = 10)
        }
    }
}
