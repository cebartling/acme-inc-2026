package com.acme.cart.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "carts")
class Cart(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "session_id", nullable = false, unique = true, length = 64)
    val sessionId: String,

    @Column(name = "customer_id")
    val customerId: UUID? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,

    @OneToMany(
        mappedBy = "cart",
        fetch = FetchType.LAZY,
        cascade = [CascadeType.ALL],
        orphanRemoval = true
    )
    @OrderBy("createdAt ASC")
    val items: MutableList<CartItem> = mutableListOf()
) {
    /** Total units across all lines, shown on the header cart badge. */
    val itemCount: Int
        get() = items.sumOf { it.quantity }

    /**
     * Adds [quantity] of a variant, merging into an existing line for the same variant
     * (AC-0004-06-04). The line's unit price is re-read from [pricing] at the new total
     * quantity, so crossing a tier threshold reprices the whole line (AC-0004-06-09).
     *
     * @return the added or updated line, or [CartError.MaxQuantityExceeded] when the
     *   variant's total would exceed [maxQuantity] — in which case the cart is unchanged.
     */
    fun addItem(
        variantId: UUID,
        quantity: Int,
        pricing: VariantPricing,
        productSnapshot: String,
        maxQuantity: Int,
        now: Instant = Instant.now()
    ): Either<CartError, CartItem> {
        require(quantity > 0) { "quantity must be positive, was $quantity" }

        val existing = items.find { it.variantId == variantId }
        val newQuantity = (existing?.quantity ?: 0) + quantity
        if (newQuantity > maxQuantity) {
            return CartError.MaxQuantityExceeded(maxQuantity).left()
        }

        val item = existing?.apply {
            this.quantity = newQuantity
            this.unitPrice = pricing.unitPriceFor(newQuantity)
            this.updatedAt = now
        } ?: CartItem(
            id = UUID.randomUUID(),
            cart = this,
            variantId = variantId,
            quantity = newQuantity,
            unitPrice = pricing.unitPriceFor(newQuantity),
            productSnapshot = productSnapshot,
            createdAt = now
        ).also { items += it }

        updatedAt = now
        return item.right()
    }
}
