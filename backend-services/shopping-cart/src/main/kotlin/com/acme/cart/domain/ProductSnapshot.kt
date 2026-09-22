package com.acme.cart.domain

import java.util.UUID

/**
 * Product details captured when an item is added (AC-0004-06-03), so later catalog
 * changes do not alter what the cart shows.
 */
data class ProductSnapshot(
    val productId: UUID,
    val name: String,
    val sku: String,
    val variantName: String,
    val imageUrl: String?,
    val attributes: Map<String, String> = emptyMap()
)
