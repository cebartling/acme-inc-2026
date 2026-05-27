package com.acme.product.domain

import java.math.BigDecimal

data class SearchFilters(
    val categories: List<String> = emptyList(),
    val priceMin: BigDecimal? = null,
    val priceMax: BigDecimal? = null
) {
    val hasFilters: Boolean get() = categories.isNotEmpty() || priceMin != null || priceMax != null

    val categoryFilter: String? get() = if (categories.isEmpty()) null else categories.joinToString(",")
}
