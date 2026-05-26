package com.acme.product.domain

import java.util.UUID

data class AutocompleteSuggestion(
    val type: String,
    val text: String,
    val productId: UUID? = null,
    val productSlug: String? = null,
    val categorySlug: String? = null
)

data class AutocompleteResult(
    val query: String,
    val suggestions: List<AutocompleteSuggestion>
)
