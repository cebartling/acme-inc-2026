package com.acme.product.application

import com.acme.product.domain.AutocompleteResult
import com.acme.product.domain.AutocompleteSuggestion
import com.acme.product.infrastructure.persistence.ProductRepository
import org.springframework.stereotype.Service

@Service
class AutocompleteUseCase(
    private val repository: ProductRepository
) {

    fun execute(query: String, limit: Int = 8): AutocompleteResult {
        val productLimit = minOf(5, limit)
        val categoryLimit = minOf(3, limit)

        val products = repository.autocompleteProducts(query, productLimit)
        val categories = repository.autocompleteCategories(query, categoryLimit)

        val suggestions = mutableListOf<AutocompleteSuggestion>()

        products.mapTo(suggestions) { p ->
            AutocompleteSuggestion(
                type = "product",
                text = p.getName(),
                productId = p.getId(),
                productSlug = p.getSlug()
            )
        }

        categories.mapTo(suggestions) { cat ->
            AutocompleteSuggestion(
                type = "category",
                text = cat,
                categorySlug = cat.lowercase().replace(" ", "-")
            )
        }

        return AutocompleteResult(
            query = query,
            suggestions = suggestions.take(limit)
        )
    }
}
