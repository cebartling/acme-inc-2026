package com.acme.product.api.v1

import java.math.BigDecimal

data class TierPricingEntry(
    val minQuantity: Int,
    val price: BigDecimal
)
