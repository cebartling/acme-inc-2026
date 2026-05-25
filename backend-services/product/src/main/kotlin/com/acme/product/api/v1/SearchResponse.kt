package com.acme.product.api.v1

import java.math.BigDecimal
import java.util.UUID

/**
 * Single product in the search response.
 *
 * @property id Product UUID.
 * @property slug URL-friendly identifier.
 * @property name Product display name.
 * @property description Product description (optional).
 * @property price Product price.
 * @property category Product category (optional).
 */
data class ProductSummaryResponse(
    val id: UUID,
    val slug: String,
    val name: String,
    val price: BigDecimal,
    val category: String? = null
)

/**
 * Response body for the product search endpoint.
 *
 * @property query The original search query.
 * @property totalResults Total matching products across all pages.
 * @property page Current page number.
 * @property pageSize Number of results per page.
 * @property totalPages Total number of pages.
 * @property results List of matching products on this page.
 * @property spellingSuggestion Alternative query suggestion when zero results found.
 * @property executionTimeMs Search execution time in milliseconds.
 */
data class SearchResponse(
    val query: String,
    val totalResults: Long,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int,
    val results: List<ProductSummaryResponse>,
    val spellingSuggestion: String?,
    val executionTimeMs: Long
)
