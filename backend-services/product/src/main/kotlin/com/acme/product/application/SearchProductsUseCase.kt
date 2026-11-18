package com.acme.product.application

import com.acme.product.domain.SearchFacets
import com.acme.product.domain.SearchQuery
import com.acme.product.domain.SearchResult
import com.acme.product.domain.SortOption
import com.acme.product.domain.events.FiltersApplied
import com.acme.product.domain.events.SearchExecuted
import com.acme.product.infrastructure.messaging.ProductEventPublisher
import com.acme.product.infrastructure.projection.CatalogSearchQueries
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID
import kotlin.math.ceil

@Service
class SearchProductsUseCase(
    private val queries: CatalogSearchQueries,
    private val eventPublisher: ProductEventPublisher
) {
    private val logger = LoggerFactory.getLogger(SearchProductsUseCase::class.java)

    fun execute(
        query: SearchQuery,
        sessionId: String? = null,
        correlationId: UUID = UUID.randomUUID()
    ): SearchResult {
        val startTime = System.currentTimeMillis()
        val filters = query.filters

        val results = queries.search(query.query, filters, query.sort, query.offset, query.pageSize)
        val totalResults = queries.count(query.query, filters)

        val executionTimeMs = System.currentTimeMillis() - startTime

        val spellingSuggestion = if (totalResults == 0L) {
            queries.spellingSuggestion(query.query)
        } else null

        val totalPages = if (totalResults == 0L) 0
        else ceil(totalResults.toDouble() / query.pageSize).toInt()

        val facets = try {
            SearchFacets(categories = queries.categoryFacets(query.query, filters.priceMin, filters.priceMax))
        } catch (ex: Exception) {
            logger.warn("Failed to compute category facets: {}", ex.message)
            SearchFacets()
        }

        val searchResult = SearchResult(
            products = results,
            totalResults = totalResults,
            page = query.page,
            pageSize = query.pageSize,
            totalPages = totalPages,
            facets = facets,
            spellingSuggestion = spellingSuggestion,
            executionTimeMs = executionTimeMs
        )

        publishEvent(SearchExecuted.create(
            query = query.query,
            totalResults = totalResults,
            page = query.page,
            executionTimeMs = executionTimeMs,
            sessionId = sessionId,
            correlationId = correlationId
        ), "SearchExecuted")

        if (filters.hasFilters) {
            publishEvent(FiltersApplied.create(
                query = query.query,
                filters = filters,
                resultCount = totalResults,
                sessionId = sessionId,
                correlationId = correlationId
            ), "FiltersApplied")
        }

        return searchResult
    }

    private fun publishEvent(event: com.acme.product.domain.events.DomainEvent, name: String) {
        try {
            eventPublisher.publish(event)
        } catch (ex: Exception) {
            logger.warn("Failed to publish {} event: {}", name, ex.message)
        }
    }
}
