package com.acme.product.api.v1

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Request body for the product search endpoint.
 *
 * @property query The search query string.
 * @property page 1-based page number.
 * @property pageSize Number of results per page.
 * @property sort Sort option (relevance, price_asc, price_desc, newest).
 * @property filters Optional filters (reserved for future use).
 */
data class SearchRequest(
    @field:NotBlank @field:Size(max = 200) val query: String,
    val page: Int = 1,
    val pageSize: Int = 24,
    val sort: String = "relevance",
    val filters: Map<String, Any> = emptyMap()
)
