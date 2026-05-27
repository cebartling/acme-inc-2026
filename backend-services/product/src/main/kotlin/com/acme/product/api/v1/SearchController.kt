package com.acme.product.api.v1

import com.acme.product.application.AutocompleteUseCase
import com.acme.product.application.SearchProductsUseCase
import com.acme.product.domain.SearchFilters
import com.acme.product.domain.SearchQuery
import com.acme.product.domain.SortOption
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for product search operations.
 *
 * Exposes a POST endpoint for full-text product search with pagination and sorting.
 */
@RestController
@RequestMapping("/api/v1")
class SearchController(
    private val searchProductsUseCase: SearchProductsUseCase,
    private val autocompleteUseCase: AutocompleteUseCase
) {

    /**
     * Searches for products matching the given query.
     *
     * @param request The search request body.
     * @param correlationId Optional correlation ID for distributed tracing.
     * @param sessionId Optional session ID for analytics.
     * @return 200 OK with search results.
     */
    @PostMapping("/search")
    fun search(
        @Valid @RequestBody request: SearchRequest,
        @RequestHeader("X-Correlation-Id", required = false) correlationId: String?,
        @RequestHeader("X-Session-Id", required = false) sessionId: String?
    ): ResponseEntity<SearchResponse> {
        val sortOption = try {
            SortOption.valueOf(request.sort.uppercase())
        } catch (_: IllegalArgumentException) {
            SortOption.RELEVANCE
        }

        val filters = SearchFilters(
            categories = request.filters.categories,
            priceMin = request.filters.priceMin,
            priceMax = request.filters.priceMax
        )

        val query = SearchQuery(
            query = request.query,
            page = request.page,
            pageSize = request.pageSize,
            sort = sortOption,
            filters = filters
        )

        val parsedCorrelationId = correlationId?.let {
            try {
                UUID.fromString(it)
            } catch (_: Exception) {
                UUID.randomUUID()
            }
        } ?: UUID.randomUUID()

        val result = searchProductsUseCase.execute(query, sessionId, parsedCorrelationId)

        val response = SearchResponse(
            query = request.query,
            totalResults = result.totalResults,
            page = result.page,
            pageSize = result.pageSize,
            totalPages = result.totalPages,
            results = result.products.map { p ->
                ProductSummaryResponse(
                    id = p.id,
                    slug = p.slug,
                    name = p.name,
                    price = p.price,
                    category = p.category
                )
            },
            facets = SearchFacetsResponse(categories = result.facets.categories),
            spellingSuggestion = result.spellingSuggestion,
            executionTimeMs = result.executionTimeMs
        )

        return ResponseEntity.ok(response)
    }

    @GetMapping("/search/autocomplete")
    fun autocomplete(
        @RequestParam("q") @Size(min = 2, max = 200) query: String,
        @RequestParam("limit", defaultValue = "8") @Min(1) @Max(20) limit: Int
    ): ResponseEntity<AutocompleteResponse> {
        val result = autocompleteUseCase.execute(query, limit)

        val response = AutocompleteResponse(
            query = result.query,
            suggestions = result.suggestions.map { s ->
                AutocompleteSuggestionResponse(
                    type = s.type,
                    text = s.text,
                    productId = s.productId,
                    productSlug = s.productSlug,
                    imageUrl = null,
                    categorySlug = s.categorySlug
                )
            }
        )

        return ResponseEntity.ok(response)
    }
}
