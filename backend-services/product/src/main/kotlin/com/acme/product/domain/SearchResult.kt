package com.acme.product.domain

import java.math.BigDecimal
import java.util.UUID

/**
 * A single product returned in search results.
 *
 * @property id Product UUID.
 * @property slug URL-friendly identifier.
 * @property name Product display name.
 * @property price Product price.
 * @property category Optional category.
 */
data class ProductSummary(
    val id: UUID,
    val slug: String,
    val name: String,
    val price: BigDecimal,
    val category: String? = null
)

/**
 * Facet information aggregated across search results.
 *
 * @property categories Map of category name to result count.
 */
data class SearchFacets(
    val categories: Map<String, Long> = emptyMap()
)

/**
 * The full result of executing a product search.
 *
 * @property products Page of matching products.
 * @property totalResults Total number of matching products (all pages).
 * @property page Current page number (1-based).
 * @property pageSize Number of results per page.
 * @property totalPages Total number of pages.
 * @property facets Aggregated facets.
 * @property spellingSuggestion Alternative query to try when zero results found.
 * @property executionTimeMs How long the search took in milliseconds.
 */
data class SearchResult(
    val products: List<ProductSummary>,
    val totalResults: Long,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int,
    val facets: SearchFacets = SearchFacets(),
    val spellingSuggestion: String? = null,
    val executionTimeMs: Long
)
