package com.acme.cart.api.v1

import com.acme.cart.application.AddItemToCartUseCase
import com.acme.cart.application.CheckoutStarted
import com.acme.cart.domain.AvailabilityIssue
import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartStatus
import com.acme.cart.domain.MergeResult
import com.acme.cart.domain.ProductSnapshot
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class AddToCartRequest(
    @field:NotNull
    val variantId: UUID?,

    @field:NotNull
    @field:Min(1)
    val quantity: Int?,

    @field:NotNull
    @field:Valid
    val productSnapshot: ProductSnapshotRequest?
)

data class ProductSnapshotRequest(
    @field:NotNull
    val productId: UUID?,

    @field:NotBlank
    val name: String?,

    @field:NotBlank
    val sku: String?,

    @field:NotBlank
    val variantName: String?,

    val imageUrl: String? = null,

    val attributes: Map<String, String> = emptyMap()
) {
    fun toDomain() = ProductSnapshot(
        productId = productId!!,
        name = name!!,
        sku = sku!!,
        variantName = variantName!!,
        imageUrl = imageUrl,
        attributes = attributes
    )
}

data class UpdateQuantityRequest(
    @field:NotNull
    @field:Min(1)
    val quantity: Int?
)

data class CartResponse(
    val id: UUID,
    /** ACTIVE, or CHECKOUT while the cart is locked for checkout (PIN-329). */
    val status: CartStatus,
    val items: List<CartItemResponse>,
    val summary: CartSummaryResponse
) {
    companion object {
        fun from(cart: Cart, snapshotOf: (String) -> ProductSnapshot) = CartResponse(
            id = cart.id,
            status = cart.status,
            items = cart.items.map {
                CartItemResponse(
                    id = it.id,
                    variantId = it.variantId,
                    quantity = it.quantity,
                    unitPrice = it.unitPrice,
                    lineTotal = it.lineTotal,
                    productSnapshot = snapshotOf(it.productSnapshot)
                )
            },
            summary = CartSummaryResponse(
                itemCount = cart.itemCount,
                subtotal = cart.subtotal,
                currency = AddItemToCartUseCase.CURRENCY
            )
        )
    }
}

data class CartItemResponse(
    val id: UUID,
    val variantId: UUID,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val lineTotal: BigDecimal,
    val productSnapshot: ProductSnapshot
)

data class CartSummaryResponse(
    val itemCount: Int,
    val subtotal: BigDecimal,
    val currency: String
)

/**
 * A started checkout (PIN-329, journey 0005 "Initiate Checkout"): the session the cart is
 * locked for, which the order service will need with the cart reference.
 */
data class CheckoutResponse(
    val checkoutSessionId: UUID,
    val cartId: UUID,
    val status: String,
    val expiresAt: Instant,
    val cart: CartSummaryResponse
) {
    companion object {
        fun from(started: CheckoutStarted) = CheckoutResponse(
            checkoutSessionId = started.session.id,
            cartId = started.cart.id,
            status = "INITIATED",
            expiresAt = started.session.expiresAt,
            cart = CartSummaryResponse(
                itemCount = started.cart.itemCount,
                subtotal = started.cart.subtotal,
                currency = AddItemToCartUseCase.CURRENCY
            )
        )
    }
}

/** A line that refused checkout (PIN-329): `OUT_OF_STOCK`, or `NOT_AVAILABLE` for a gone variant. */
data class CheckoutValidationErrorResponse(
    val cartItemId: UUID,
    val variantId: UUID,
    val productName: String,
    val issue: AvailabilityIssue
)

/**
 * The signed-in user's cart after a merge (US-0004-08), plus what the merge did.
 * [mergeResult] is null when there was nothing to merge.
 */
data class MergeResponse(
    val id: UUID,
    /** As in [CartResponse]: the account cart can already be locked for checkout (PIN-329). */
    val status: CartStatus,
    val items: List<CartItemResponse>,
    val summary: CartSummaryResponse,
    val mergeResult: MergeResultResponse?
) {
    companion object {
        fun from(cart: CartResponse, result: MergeResult?, snapshotOf: (String) -> ProductSnapshot) = MergeResponse(
            id = cart.id,
            status = cart.status,
            items = cart.items,
            summary = cart.summary,
            mergeResult = result?.let { r ->
                MergeResultResponse(
                    itemsMerged = r.itemsMerged,
                    quantitiesAdjusted = r.quantitiesAdjusted.map {
                        QuantityAdjustmentResponse(it.variantId, it.requestedTotal, it.adjustedTo)
                    },
                    itemsUnavailable = r.itemsUnavailable.map {
                        UnavailableItemResponse(it.variantId, snapshotOf(it.productSnapshot))
                    }
                )
            }
        )
    }
}

data class MergeResultResponse(
    val itemsMerged: Int,
    val quantitiesAdjusted: List<QuantityAdjustmentResponse>,
    val itemsUnavailable: List<UnavailableItemResponse>
)

/** A guest line left out of a merge because its variant is no longer found (PIN-306). */
data class UnavailableItemResponse(
    val variantId: UUID,
    val productSnapshot: ProductSnapshot
)

data class QuantityAdjustmentResponse(
    val variantId: UUID,
    val requestedTotal: Int,
    val adjustedTo: Int,
    val reason: String = "MAX_ORDER_QUANTITY"
)
