package com.acme.cart.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Duration
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

    /**
     * When the owner last used the cart: every change sets it, and a guest viewing the cart
     * refreshes it at most daily. An ACTIVE guest cart idle past the guest TTL expires (PIN-287).
     */
    @Column(name = "last_active_at", nullable = false)
    var lastActiveAt: Instant = createdAt,

    /**
     * Optimistic lock (PIN-278). Every change goes through [touch], which dirties this row, so
     * the version moves even when only a line changed, and a save from a stale copy fails.
     * Null until first saved: ids are assigned in code, so a null version is how Spring Data
     * tells a new cart from an existing one.
     */
    @Version
    @Column(name = "version")
    var version: Long? = null,

    /** The checkout session a CHECKOUT cart is locked for (PIN-329); null otherwise. */
    @Column(name = "checkout_session_id")
    var checkoutSessionId: UUID? = null,

    /** When a CHECKOUT cart's checkout session lapses (PIN-329); null otherwise. */
    @Column(name = "checkout_expires_at")
    var checkoutExpiresAt: Instant? = null,

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

    /** Sum of the line totals. */
    val subtotal: BigDecimal
        get() = items.fold(BigDecimal.ZERO) { sum, item -> sum + item.lineTotal }

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
        lockedError()?.let { return it.left() }

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

        touch(now)
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
        lockedError()?.let { return it.left() }

        val item = items.find { it.id == itemId } ?: return CartError.CartItemNotFound(itemId).left()
        if (quantity > maxQuantity) {
            return CartError.MaxQuantityExceeded(maxQuantity).left()
        }

        val previousQuantity = item.quantity
        item.quantity = quantity
        item.unitPrice = pricing.unitPriceFor(quantity)
        item.updatedAt = now
        touch(now)
        return QuantityChange(item, previousQuantity).right()
    }

    /** Removes a line (AC-0004-07-05). Removing the last line leaves an empty cart. */
    fun removeItem(itemId: UUID, now: Instant = Instant.now()): Either<CartError, CartItem> {
        lockedError()?.let { return it.left() }
        val item = items.find { it.id == itemId } ?: return CartError.CartItemNotFound(itemId).left()
        items.remove(item)
        touch(now)
        return item.right()
    }

    /**
     * Removes every line (PIN-294), leaving an empty cart that still belongs to its owner.
     * Clearing an empty cart changes nothing. Returns the removed lines.
     */
    fun clear(now: Instant = Instant.now()): Either<CartError, List<CartItem>> {
        lockedError()?.let { return it.left() }
        if (items.isEmpty()) return emptyList<CartItem>().right()
        val removed = items.toList()
        items.clear()
        touch(now)
        return removed.right()
    }

    /**
     * Moves a guest cart's lines into this (the signed-in user's) cart (US-0004-08).
     *
     * A variant in both carts becomes one line with the quantities summed (AC-03), capped at
     * [maxQuantity] (AC-04). Every line that changes is repriced at its new quantity from
     * [pricing], so a merged total that crosses a tier gets the tier price. The guest cart
     * is then MERGED, so its session no longer resolves to it (AC-05).
     *
     * @param pricing current pricing for every variant in [guest] that is not [unavailable].
     * @param unavailable variants the product service no longer finds (PIN-306): their lines stay
     *   in the MERGED guest cart and are reported in [MergeResult.itemsUnavailable].
     */
    fun absorb(
        guest: Cart,
        pricing: Map<UUID, VariantPricing>,
        maxQuantity: Int,
        unavailable: Set<UUID> = emptySet(),
        now: Instant = Instant.now()
    ): MergeResult {
        require(guest !== this) { "a cart cannot absorb itself" }
        require(guest.status == CartStatus.ACTIVE) { "cart ${guest.id} was already merged" }
        // A locked cart refuses changes (PIN-329); the merge use case checks before absorbing.
        require(status == CartStatus.ACTIVE) { "cart $id is $status and cannot absorb a guest cart" }

        // A variant the product service no longer finds (PIN-306) can't be priced; its line stays
        // behind in the MERGED guest cart and is reported instead.
        val (dropped, carried) = guest.items.partition { it.variantId in unavailable }
        val adjustments = carried.mapNotNull { guestLine ->
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
        touch(now)
        return MergeResult(
            itemsMerged = carried.size,
            quantitiesAdjusted = adjustments,
            itemsUnavailable = dropped.map { UnavailableItem(it.variantId, it.productSnapshot) }
        )
    }

    /**
     * Locks the cart for checkout (PIN-329): it becomes CHECKOUT with a new checkout session
     * lasting [sessionLength], and refuses every change until checkout ends. Starting again
     * while the session is live resumes it (PIN-330): same session, expiry pushed out to
     * [sessionLength] from [now]. A session that has lapsed but not yet been unlocked is
     * replaced by a new one, as if the cart were ACTIVE.
     *
     * @return the checkout session, or [CartError.CartEmpty] with the cart unchanged.
     */
    fun startCheckout(sessionLength: Duration, now: Instant = Instant.now()): Either<CartError, CheckoutSession> {
        if (isInLiveCheckout(now)) {
            checkoutExpiresAt = now.plus(sessionLength)
            touch(now)
            return checkoutSession().right()
        }
        check(status in CartStatus.CURRENT) { "cart $id is $status and cannot start checkout" }
        if (items.isEmpty()) return CartError.CartEmpty(id).left()

        status = CartStatus.CHECKOUT
        checkoutSessionId = UUID.randomUUID()
        checkoutExpiresAt = now.plus(sessionLength)
        touch(now)
        return checkoutSession().right()
    }

    /** Whether the cart is in checkout and its session has not lapsed as of [now] (PIN-330). */
    fun isInLiveCheckout(now: Instant): Boolean =
        status == CartStatus.CHECKOUT && checkoutSession().expiresAt > now

    /**
     * The checkout session the cart is still locked for although it lapsed by [now], because the
     * unlock job has not reached it yet (PIN-330); null if the cart is not in checkout or its
     * session is live.
     */
    fun lapsedCheckoutSession(now: Instant): CheckoutSession? =
        if (status == CartStatus.CHECKOUT && !isInLiveCheckout(now)) checkoutSession() else null

    /**
     * Leaves checkout (PIN-330): the cart is ACTIVE again with its lines, and its checkout
     * session is gone. Returns the session left, or null if the cart was not in checkout,
     * in which case nothing changes, so leaving twice is not an error.
     */
    fun abandonCheckout(now: Instant = Instant.now()): CheckoutSession? {
        if (status != CartStatus.CHECKOUT) return null
        val session = checkoutSession()
        status = CartStatus.ACTIVE
        checkoutSessionId = null
        checkoutExpiresAt = null
        touch(now)
        return session
    }

    private fun checkoutSession() = CheckoutSession(
        id = checkNotNull(checkoutSessionId) { "CHECKOUT cart $id has no checkout session" },
        expiresAt = checkNotNull(checkoutExpiresAt) { "CHECKOUT cart $id has no checkout expiry" }
    )

    private fun lockedError(): CartError? = if (status == CartStatus.CHECKOUT) CartError.CartLocked(id) else null

    /** A change by the owner: the cart is both modified and in use. */
    private fun touch(now: Instant) {
        updatedAt = now
        lastActiveAt = now
    }
}

data class QuantityChange(val item: CartItem, val previousQuantity: Int)

/** The checkout session a cart is locked for (PIN-329). */
data class CheckoutSession(val id: UUID, val expiresAt: Instant)

/**
 * What a merge did: how many guest lines moved, which quantities were capped, and which lines
 * were left out because their variant is no longer found (PIN-306).
 */
data class MergeResult(
    val itemsMerged: Int,
    val quantitiesAdjusted: List<QuantityAdjustment>,
    val itemsUnavailable: List<UnavailableItem> = emptyList()
)

/** A merged variant whose summed quantity ([requestedTotal]) was capped to [adjustedTo]. */
data class QuantityAdjustment(val variantId: UUID, val requestedTotal: Int, val adjustedTo: Int)

/** A guest line left out of a merge; [productSnapshot] is its stored JSON, so the client can name it. */
data class UnavailableItem(val variantId: UUID, val productSnapshot: String)

/** A new, empty ACTIVE cart for [owner]. */
fun newCartFor(owner: CartOwner): Cart = when (owner) {
    is CartOwner.Guest -> Cart(id = UUID.randomUUID(), sessionId = owner.sessionId)
    is CartOwner.Customer -> Cart(id = UUID.randomUUID(), userId = owner.userId)
}
