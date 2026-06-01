package com.acme.product.api.v1

import java.math.BigDecimal
import java.util.UUID

data class ProductDetailResponse(
    val id: UUID,
    val slug: String,
    val name: String,
    val description: String?,
    val price: BigDecimal,
    val category: String?,
    val tags: List<String>,
    val availability: String,
    val relatedProducts: List<ProductSummaryResponse>,
    val variants: List<ProductVariantResponse> = emptyList()
)
