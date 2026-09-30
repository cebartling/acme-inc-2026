package com.acme.product.application

import com.acme.product.infrastructure.persistence.ProductStockProjection
import io.mockk.every
import io.mockk.mockk
import java.util.UUID

/** A [ProductStockProjection] row for the use-case tests that mock the stock lookup (PIN-273). */
internal fun stockProjection(id: UUID, inStock: Boolean, imageUrl: String? = null): ProductStockProjection {
    val projection = mockk<ProductStockProjection>()
    every { projection.getProductId() } returns id
    every { projection.getInStock() } returns inStock
    every { projection.getImageUrl() } returns imageUrl
    return projection
}
