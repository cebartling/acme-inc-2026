package com.acme.product.application

import com.acme.product.domain.Product
import com.acme.product.domain.ProductSummary
import com.acme.product.infrastructure.persistence.ProductRepository
import com.acme.product.infrastructure.persistence.ProductStockProjection
import java.math.BigDecimal
import java.util.UUID

/**
 * Stock and card image for a page of products, in one query (US-0004-10, PIN-273). A product
 * missing from the result counts as in stock with no image, like a product without variants.
 */
internal fun ProductRepository.stockOf(ids: Collection<UUID>): Map<UUID, ProductStockProjection> =
    if (ids.isEmpty()) emptyMap() else findStockSummaries(ids).associateBy { it.getProductId() }

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
