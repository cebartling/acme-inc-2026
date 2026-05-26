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

        val safePrefix = escapeLikePattern(query)
        val products = repository.autocompleteProducts(safePrefix, productLimit)
        val categories = repository.autocompleteCategories(safePrefix, categoryLimit)

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
                categorySlug = cat.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            )
        }

        return AutocompleteResult(
            query = query,
            suggestions = suggestions.take(limit)
        )
    }

    private fun escapeLikePattern(s: String): String =
        s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
