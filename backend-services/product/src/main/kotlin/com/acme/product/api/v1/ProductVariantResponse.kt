package com.acme.product.api.v1

import java.math.BigDecimal
import java.util.UUID

data class ProductVariantResponse(
    val id: UUID,
    val sku: String,
    val name: String,
    val color: String?,
    val size: String?,
    val isDefault: Boolean,
    val inStock: Boolean,
    val priceOverride: BigDecimal?,
    val images: List<String>,
    val tierPricing: List<TierPricingEntry>
)
