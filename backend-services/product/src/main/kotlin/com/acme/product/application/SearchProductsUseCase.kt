package com.acme.product.application

import com.acme.product.domain.ProductSummary
import com.acme.product.domain.SearchFacets
import com.acme.product.domain.SearchQuery
import com.acme.product.domain.SearchResult
import com.acme.product.domain.SortOption
import com.acme.product.domain.events.FiltersApplied
import com.acme.product.domain.events.SearchExecuted
import com.acme.product.infrastructure.messaging.ProductEventPublisher
import com.acme.product.infrastructure.persistence.ProductRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID
import kotlin.math.ceil

@Service
class SearchProductsUseCase(
    private val repository: ProductRepository,
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

        val results = if (filters.hasFilters) {
            when (query.sort) {
                SortOption.RELEVANCE -> repository.searchByRelevanceFiltered(
                    query.query, filters.categoryFilter, filters.priceMin, filters.priceMax, query.pageSize, query.offset
                )
                SortOption.PRICE_ASC -> repository.searchByPriceAscFiltered(
                    query.query, filters.categoryFilter, filters.priceMin, filters.priceMax, query.pageSize, query.offset
                )
                SortOption.PRICE_DESC -> repository.searchByPriceDescFiltered(
                    query.query, filters.categoryFilter, filters.priceMin, filters.priceMax, query.pageSize, query.offset
                )
                SortOption.NEWEST -> repository.searchByNewestFiltered(
                    query.query, filters.categoryFilter, filters.priceMin, filters.priceMax, query.pageSize, query.offset
                )
            }
        } else {
            when (query.sort) {
                SortOption.RELEVANCE -> repository.searchByRelevance(query.query, query.pageSize, query.offset)
                SortOption.PRICE_ASC -> repository.searchByPriceAsc(query.query, query.pageSize, query.offset)
                SortOption.PRICE_DESC -> repository.searchByPriceDesc(query.query, query.pageSize, query.offset)
                SortOption.NEWEST -> repository.searchByNewest(query.query, query.pageSize, query.offset)
            }
        }

        val totalResults = if (filters.hasFilters) {
            repository.countByQueryFiltered(query.query, filters.categoryFilter, filters.priceMin, filters.priceMax)
        } else {
            repository.countByQuery(query.query)
        }

        val executionTimeMs = System.currentTimeMillis() - startTime

        val spellingSuggestion = if (totalResults == 0L) {
            repository.findSpellingSuggestion(query.query)
        } else null

        val totalPages = if (totalResults == 0L) 0
        else ceil(totalResults.toDouble() / query.pageSize).toInt()

        val facets = try {
            val rows = repository.getCategoryFacets(query.query, filters.priceMin, filters.priceMax)
            SearchFacets(categories = rows.associate { it.getCategory() to it.getCount() })
        } catch (ex: Exception) {
            logger.warn("Failed to compute category facets: {}", ex.message)
            SearchFacets()
        }

        val searchResult = SearchResult(
            products = results.map { p ->
                ProductSummary(
                    id = p.getId(),
                    slug = p.getSlug(),
                    name = p.getName(),
                    price = p.getPrice(),
                    category = p.getCategory()
                )
            },
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
