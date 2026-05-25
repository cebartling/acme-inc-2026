package com.acme.product.application

import com.acme.product.domain.ProductSummary
import com.acme.product.domain.SearchQuery
import com.acme.product.domain.SearchResult
import com.acme.product.domain.SortOption
import com.acme.product.domain.events.SearchExecuted
import com.acme.product.infrastructure.messaging.ProductEventPublisher
import com.acme.product.infrastructure.persistence.ProductRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID
import kotlin.math.ceil

/**
 * Application service that executes product searches.
 *
 * Delegates to [ProductRepository] for full-text search and publishes
 * [SearchExecuted] analytics events via [ProductEventPublisher].
 */
@Service
class SearchProductsUseCase(
    private val repository: ProductRepository,
    private val eventPublisher: ProductEventPublisher
) {
    private val logger = LoggerFactory.getLogger(SearchProductsUseCase::class.java)

    /**
     * Executes a product search and publishes an analytics event.
     *
     * @param query The search query parameters.
     * @param sessionId Optional session identifier for analytics.
     * @param correlationId Correlation ID for distributed tracing.
     * @return The search result containing matching products and metadata.
     */
    fun execute(
        query: SearchQuery,
        sessionId: String? = null,
        correlationId: UUID = UUID.randomUUID()
    ): SearchResult {
        val startTime = System.currentTimeMillis()

        val results = when (query.sort) {
            SortOption.RELEVANCE -> repository.searchByRelevance(query.query, query.pageSize, query.offset)
            SortOption.PRICE_ASC -> repository.searchByPriceAsc(query.query, query.pageSize, query.offset)
            SortOption.PRICE_DESC -> repository.searchByPriceDesc(query.query, query.pageSize, query.offset)
            SortOption.NEWEST -> repository.searchByNewest(query.query, query.pageSize, query.offset)
        }

        val totalResults = repository.countByQuery(query.query)
        val executionTimeMs = System.currentTimeMillis() - startTime

        val spellingSuggestion = if (totalResults == 0L) {
            repository.findSpellingSuggestion(query.query)
        } else null

        val totalPages = if (totalResults == 0L) 0
        else ceil(totalResults.toDouble() / query.pageSize).toInt()

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
            spellingSuggestion = spellingSuggestion,
            executionTimeMs = executionTimeMs
        )

        val event = SearchExecuted.create(
            query = query.query,
            totalResults = totalResults,
            page = query.page,
            executionTimeMs = executionTimeMs,
            sessionId = sessionId,
            correlationId = correlationId
        )

        try {
            eventPublisher.publish(event)
        } catch (ex: Exception) {
            logger.warn("Failed to publish SearchExecuted event: {}", ex.message)
        }

        return searchResult
    }
}
