package com.acme.product.application

import com.acme.product.domain.Product
import com.acme.product.domain.ProductNotFoundException
import com.acme.product.domain.ProductStatus
import com.acme.product.domain.ProductSummary
import com.acme.product.domain.events.ProductViewed
import com.acme.product.infrastructure.messaging.ProductEventPublisher
import com.acme.product.infrastructure.persistence.ProductRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetProductDetailUseCase(
    private val repository: ProductRepository,
    private val eventPublisher: ProductEventPublisher
) {
    private val logger = LoggerFactory.getLogger(GetProductDetailUseCase::class.java)

    data class Result(
        val product: Product,
        val relatedProducts: List<ProductSummary>
    )

    fun execute(
        slug: String,
        sessionId: String? = null,
        correlationId: UUID = UUID.randomUUID()
    ): Result {
        val product = repository.findBySlugAndStatus(slug, ProductStatus.PUBLISHED)
            .orElseThrow { ProductNotFoundException(slug) }

        val category = product.category
        val relatedProducts = if (category != null) inStockRelated(category, product.id) else emptyList()

        publishEvent(ProductViewed.create(
            productId = product.id,
            slug = product.slug,
            sessionId = sessionId,
            correlationId = correlationId
        ), "ProductViewed")

        return Result(product = product, relatedProducts = relatedProducts)
    }

    /**
     * Up to [RELATED_LIMIT] in-stock products from the same category, newest first. They double
     * as alternatives when the viewed variant is out of stock (US-0004-10 AC-04), so more
     * candidates are read than shown and the out-of-stock ones are dropped.
     */
    private fun inStockRelated(category: String, excludeId: UUID): List<ProductSummary> {
        val candidates = repository.findRelatedProducts(category, excludeId, PageRequest.of(0, RELATED_CANDIDATES))
        val stock = repository.stockOf(candidates.map { it.id })
        return candidates.map { it.toSummary(stock[it.id]) }.filter { it.inStock }.take(RELATED_LIMIT)
    }

    private fun publishEvent(event: com.acme.product.domain.events.DomainEvent, name: String) {
        try {
            eventPublisher.publish(event)
        } catch (ex: Exception) {
            logger.warn("Failed to publish {} event: {}", name, ex.message)
        }
    }

    private companion object {
        const val RELATED_LIMIT = 4
        const val RELATED_CANDIDATES = 12
    }
}
