package com.acme.cart.domain

import java.math.BigDecimal

/**
 * A variant's price as reported by the product service, including quantity tiers.
 */
data class VariantPricing(
    val price: BigDecimal,
    val tiers: List<PriceTier> = emptyList()
) {
    /** Unit price for [quantity]: the tier with the highest threshold met, else the base price. */
    fun unitPriceFor(quantity: Int): BigDecimal =
        tiers.filter { it.minQuantity <= quantity }
            .maxByOrNull { it.minQuantity }
            ?.price
            ?: price
}

data class PriceTier(val minQuantity: Int, val price: BigDecimal)
