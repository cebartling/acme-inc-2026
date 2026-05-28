package com.acme.product.application

import com.acme.product.domain.Product
import com.acme.product.domain.ProductNotFoundException
import com.acme.product.domain.ProductStatus
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
        val relatedProducts: List<Product>
    )

    fun execute(
        slug: String,
        sessionId: String? = null,
        correlationId: UUID = UUID.randomUUID()
    ): Result {
        val product = repository.findBySlugAndStatus(slug, ProductStatus.PUBLISHED)
            .orElseThrow { ProductNotFoundException(slug) }

        val category = product.category
        val relatedProducts = if (category != null) {
            repository.findRelatedProducts(category, product.id, PageRequest.of(0, 4))
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
}
