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

    /**
     * Moves a guest cart's lines into this (the signed-in user's) cart (US-0004-08).
     *
     * A variant in both carts becomes one line with the quantities summed (AC-03), capped at
     * [maxQuantity] (AC-04). Every line that changes is repriced at its new quantity from
     * [pricing], so a merged total that crosses a tier gets the tier price. The guest cart
     * is then MERGED, so its session no longer resolves to it (AC-05).
     *
     * @param pricing current pricing for every variant in [guest].
     */
    fun absorb(
        guest: Cart,
        pricing: Map<UUID, VariantPricing>,
        maxQuantity: Int,
        now: Instant = Instant.now()
    ): MergeResult {
        require(guest !== this) { "a cart cannot absorb itself" }
        require(guest.status == CartStatus.ACTIVE) { "cart ${guest.id} was already merged" }

        val adjustments = guest.items.mapNotNull { guestLine ->
            val variantPricing = requireNotNull(pricing[guestLine.variantId]) {
                "no pricing for variant ${guestLine.variantId}"
            }
            val existing = items.find { it.variantId == guestLine.variantId }
            val requested = (existing?.quantity ?: 0) + guestLine.quantity
            val quantity = minOf(requested, maxQuantity)

            if (existing != null) {
                existing.quantity = quantity
                existing.unitPrice = variantPricing.unitPriceFor(quantity)
                existing.updatedAt = now
            } else {
                items += CartItem(
                    id = UUID.randomUUID(),
                    cart = this,
                    variantId = guestLine.variantId,
                    quantity = quantity,
                    unitPrice = variantPricing.unitPriceFor(quantity),
                    productSnapshot = guestLine.productSnapshot,
                    createdAt = now
                )
            }

            if (requested > quantity) QuantityAdjustment(guestLine.variantId, requested, quantity) else null
        }

        guest.status = CartStatus.MERGED
        guest.updatedAt = now
        updatedAt = now
        return MergeResult(itemsMerged = guest.items.size, quantitiesAdjusted = adjustments)
    }
}

data class QuantityChange(val item: CartItem, val previousQuantity: Int)

/** What a merge did: how many guest lines moved, and which quantities were capped. */
data class MergeResult(val itemsMerged: Int, val quantitiesAdjusted: List<QuantityAdjustment>)

/** A merged variant whose summed quantity ([requestedTotal]) was capped to [adjustedTo]. */
data class QuantityAdjustment(val variantId: UUID, val requestedTotal: Int, val adjustedTo: Int)

/** A new, empty ACTIVE cart for [owner]. */
fun newCartFor(owner: CartOwner): Cart = when (owner) {
    is CartOwner.Guest -> Cart(id = UUID.randomUUID(), sessionId = owner.sessionId)
    is CartOwner.Customer -> Cart(id = UUID.randomUUID(), userId = owner.userId)
}
