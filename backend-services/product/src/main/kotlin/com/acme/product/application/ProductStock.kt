package com.acme.product.application

import com.acme.product.domain.Product
import com.acme.product.domain.ProductSummary
import com.acme.product.infrastructure.persistence.ProductRepository
import com.acme.product.infrastructure.persistence.ProductStockProjection
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.util.UUID

private val logger = LoggerFactory.getLogger("com.acme.product.application.ProductStock")

/**
 * Stock and card image for a page of products, in one query (US-0004-10, PIN-273). A product
 * missing from the result counts as in stock with no image, like a product without variants.
 *
 * Best-effort, like search's facets: if the lookup fails, it is logged and every product counts
 * as in stock with no image, so search and the category fallback (US-0004-09) keep answering.
 * A wrong "in stock" can't be ordered: add to cart re-checks the variant's own availability.
 */
internal fun ProductRepository.stockOf(ids: Collection<UUID>): Map<UUID, ProductStockProjection> {
    if (ids.isEmpty()) return emptyMap()
    return try {
        findStockSummaries(ids).associateBy { it.getProductId() }
    } catch (ex: Exception) {
        logger.warn("Stock lookup failed for {} products; showing them in stock: {}", ids.size, ex.message)
        emptyMap()
    }
}

internal fun summaryOf(
    id: UUID,
    slug: String,
    name: String,
    price: BigDecimal,
    category: String?,
    stock: ProductStockProjection?
) = ProductSummary(
    id = id,
    slug = slug,
    name = name,
    price = price,
    category = category,
    inStock = stock?.getInStock() ?: true,
    imageUrl = stock?.getImageUrl()
)

internal fun Product.toSummary(stock: ProductStockProjection?) = summaryOf(id, slug, name, price, category, stock)
