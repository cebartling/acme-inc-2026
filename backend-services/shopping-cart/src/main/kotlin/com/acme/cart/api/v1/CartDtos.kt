package com.acme.cart.api.v1

import com.acme.cart.application.AddItemToCartUseCase
import com.acme.cart.domain.Cart
import com.acme.cart.domain.ProductSnapshot
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
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

data class CartResponse(
    val id: UUID,
    val items: List<CartItemResponse>,
    val summary: CartSummaryResponse
) {
    companion object {
        fun from(cart: Cart, snapshotOf: (String) -> ProductSnapshot) = CartResponse(
            id = cart.id,
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
                subtotal = cart.items.fold(BigDecimal.ZERO) { sum, item -> sum + item.lineTotal },
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
