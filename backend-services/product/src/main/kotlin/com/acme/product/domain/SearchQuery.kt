package com.acme.product.domain

/**
 * Sort options for search results.
 */
enum class SortOption {
    RELEVANCE,
    PRICE_ASC,
    PRICE_DESC,
    NEWEST
}

/**
 * Encapsulates input parameters for a product search.
 *
 * @property query The full-text search query string.
 * @property page 1-based page number.
 * @property pageSize Number of results per page (max 100).
 * @property sort How to order the results.
 */
data class SearchQuery(
    val query: String,
    val page: Int = 1,
    val pageSize: Int = 24,
    val sort: SortOption = SortOption.RELEVANCE,
    val filters: SearchFilters = SearchFilters()
) {
    val offset: Int get() = (page - 1) * pageSize
}
