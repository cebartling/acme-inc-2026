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

    /** The guest session that owns this cart; null for a signed-in user's cart. */
    @Column(name = "session_id", length = 64)
    val sessionId: String? = null,

    /** The signed-in user (JWT `sub`) that owns this cart; null for a guest cart. */
    @Column(name = "user_id")
    val userId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: CartStatus = CartStatus.ACTIVE,

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
    init {
        require((sessionId == null) != (userId == null)) {
            "a cart belongs to exactly one of a session or a user (session=$sessionId, user=$userId)"
        }
    }

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

    /**
     * Sets a line's quantity (AC-0004-07-04), repricing it at the new quantity so moving
     * across a tier threshold in either direction changes the unit price.
     *
     * @return the change, or an error with the cart unchanged when the item is not in
     *   this cart or [quantity] exceeds [maxQuantity].
     */
    fun updateItemQuantity(
        itemId: UUID,
        quantity: Int,
        pricing: VariantPricing,
        maxQuantity: Int,
        now: Instant = Instant.now()
    ): Either<CartError, QuantityChange> {
        require(quantity > 0) { "quantity must be positive, was $quantity" }

        val item = items.find { it.id == itemId } ?: return CartError.CartItemNotFound(itemId).left()
        if (quantity > maxQuantity) {
            return CartError.MaxQuantityExceeded(maxQuantity).left()
        }

        val previousQuantity = item.quantity
        item.quantity = quantity
        item.unitPrice = pricing.unitPriceFor(quantity)
        item.updatedAt = now
        updatedAt = now
        return QuantityChange(item, previousQuantity).right()
    }

    /** Removes a line (AC-0004-07-05). Removing the last line leaves an empty cart. */
    fun removeItem(itemId: UUID, now: Instant = Instant.now()): Either<CartError, CartItem> {
        val item = items.find { it.id == itemId } ?: return CartError.CartItemNotFound(itemId).left()
        items.remove(item)
        updatedAt = now
        return item.right()
    }
}

data class QuantityChange(val item: CartItem, val previousQuantity: Int)

/** A new, empty ACTIVE cart for [owner]. */
fun newCartFor(owner: CartOwner): Cart = when (owner) {
    is CartOwner.Guest -> Cart(id = UUID.randomUUID(), sessionId = owner.sessionId)
    is CartOwner.Customer -> Cart(id = UUID.randomUUID(), userId = owner.userId)
}
