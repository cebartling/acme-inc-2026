package com.acme.product.api.v1

import java.util.UUID

data class AutocompleteSuggestionResponse(
    val type: String,
    val text: String,
    val productId: UUID? = null,
    val productSlug: String? = null,
    val imageUrl: String? = null,
    val categorySlug: String? = null
)

data class AutocompleteResponse(
    val query: String,
    val suggestions: List<AutocompleteSuggestionResponse>
)
