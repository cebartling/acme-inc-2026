package com.acme.product.application

import com.acme.product.domain.CatalogProduct
import com.acme.product.domain.ProductNotFoundException
import com.acme.product.domain.ProductSummary
import com.acme.product.domain.events.ProductViewed
import com.acme.product.infrastructure.messaging.ProductEventPublisher
import com.acme.product.infrastructure.projection.CatalogReadModelQueries
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetProductDetailUseCase(
    private val queries: CatalogReadModelQueries,
    private val eventPublisher: ProductEventPublisher
) {
    private val logger = LoggerFactory.getLogger(GetProductDetailUseCase::class.java)

    data class Result(
        val product: CatalogProduct,
        val relatedProducts: List<ProductSummary>
    )

    fun execute(
        slug: String,
        sessionId: String? = null,
        correlationId: UUID = UUID.randomUUID()
    ): Result {
        val product = queries.findBySlug(slug) ?: throw ProductNotFoundException(slug)

        val category = product.category
        val relatedProducts = if (category != null) {
            queries.findInStockRelated(category, product.id, RELATED_LIMIT)
        } else {
            emptyList()
        }

        publishEvent(ProductViewed.create(
            productId = product.id,
            slug = product.slug,
            sessionId = sessionId,
            correlationId = correlationId
        ), "ProductViewed")

        return Result(product = product, relatedProducts = relatedProducts)
    }

    private fun publishEvent(event: com.acme.product.domain.events.DomainEvent, name: String) {
        try {
            eventPublisher.publish(event)
        } catch (ex: Exception) {
            logger.warn("Failed to publish {} event: {}", name, ex.message)
        }
    }

    private companion object {
        /**
         * Up to this many in-stock products from the same category, newest first. They double as
         * alternatives when the viewed variant is out of stock (US-0004-10 AC-04).
         */
        const val RELATED_LIMIT = 4
    }
}
