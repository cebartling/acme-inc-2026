package com.acme.product.application

import com.acme.product.domain.AutocompleteResult
import com.acme.product.domain.AutocompleteSuggestion
import com.acme.product.infrastructure.projection.CatalogSearchQueries
import org.springframework.stereotype.Service

@Service
class AutocompleteUseCase(
    private val queries: CatalogSearchQueries
) {

    fun execute(query: String, limit: Int = 8): AutocompleteResult {
        val productLimit = minOf(5, limit)
        val categoryLimit = minOf(3, limit)

        val products = queries.autocompleteProducts(query, productLimit)
        val categories = queries.autocompleteCategories(query, categoryLimit)

        val suggestions = mutableListOf<AutocompleteSuggestion>()

        products.mapTo(suggestions) { p ->
            AutocompleteSuggestion(
                type = "product",
                text = p.name,
                productId = p.id,
                productSlug = p.slug
            )
        }

        categories.mapTo(suggestions) { cat ->
            AutocompleteSuggestion(
                type = "category",
                text = cat,
                categorySlug = cat.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            )
        }

        return AutocompleteResult(
            query = query,
            suggestions = suggestions.take(limit)
        )
    }
}
