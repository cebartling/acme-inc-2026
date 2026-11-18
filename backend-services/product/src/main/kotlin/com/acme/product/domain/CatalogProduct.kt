package com.acme.product.domain

import java.math.BigDecimal
import java.util.UUID

/**
 * A published product as the catalog read model holds it (PIN-550), with its variants.
 *
 * [inStock] and [imageUrl] are derived when the read model is built: a product is in stock when it
 * has no variants or any variant is in stock, and its image is the first image of its first
 * variant (the default one, else the oldest).
 */
data class CatalogProduct(
    val id: UUID,
    val slug: String,
    val name: String,
    val description: String?,
    val price: BigDecimal,
    val category: String?,
    val tags: String?,
    val inStock: Boolean,
    val imageUrl: String?,
    val variants: List<CatalogVariant>
)

data class CatalogVariant(
    val id: UUID,
    val sku: String,
    val name: String,
    val color: String?,
    val size: String?,
    val isDefault: Boolean,
    val inStock: Boolean,
    val priceOverride: BigDecimal?,
    val images: List<String>,
    val tierPricing: List<TierPrice>
)

data class TierPrice(
    val minQuantity: Int,
    val price: BigDecimal
)

/** A published product's variant with the product's own price, which the variant's price falls back to. */
data class VariantOfProduct(
    val variant: CatalogVariant,
    val productPrice: BigDecimal
)

data class CategoryCount(
    val name: String,
    val productCount: Long
)
