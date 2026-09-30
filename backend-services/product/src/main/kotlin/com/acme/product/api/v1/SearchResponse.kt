package com.acme.product.api.v1

import com.acme.product.domain.ProductSummary
import java.math.BigDecimal
import java.util.UUID

data class ProductSummaryResponse(
    val id: UUID,
    val slug: String,
    val name: String,
    val price: BigDecimal,
    val category: String? = null,
    val inStock: Boolean = true,
    val imageUrl: String? = null
) {
    companion object {
        fun from(p: ProductSummary) = ProductSummaryResponse(
            id = p.id,
            slug = p.slug,
            name = p.name,
            price = p.price,
            category = p.category,
            inStock = p.inStock,
            imageUrl = p.imageUrl
        )
    }
}

data class SearchFacetsResponse(
    val categories: Map<String, Long> = emptyMap()
)

data class SearchResponse(
    val query: String,
    val totalResults: Long,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int,
    val results: List<ProductSummaryResponse>,
    val facets: SearchFacetsResponse,
    val spellingSuggestion: String?,
    val executionTimeMs: Long
)
