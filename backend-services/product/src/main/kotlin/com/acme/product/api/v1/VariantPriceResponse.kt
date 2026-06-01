package com.acme.product.api.v1

import java.math.BigDecimal
import java.util.UUID

data class VariantPriceResponse(
    val variantId: UUID,
    val price: BigDecimal,
    val originalPrice: BigDecimal?,
    val tierPricing: List<TierPricingEntry>
)
