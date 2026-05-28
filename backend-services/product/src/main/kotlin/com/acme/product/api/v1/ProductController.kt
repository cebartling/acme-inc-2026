package com.acme.product.api.v1

import com.acme.product.application.GetProductDetailUseCase
import com.acme.product.domain.ProductStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/products")
class ProductController(
    private val getProductDetailUseCase: GetProductDetailUseCase
) {

    @GetMapping("/{slug}")
    fun getProduct(
        @PathVariable slug: String,
        @RequestHeader("X-Session-Id", required = false) sessionId: String?,
        @RequestHeader("X-Correlation-Id", required = false) correlationId: String?
    ): ResponseEntity<ProductDetailResponse> {
        val parsedCorrelationId = correlationId?.let {
            try { UUID.fromString(it) } catch (_: Exception) { UUID.randomUUID() }
        } ?: UUID.randomUUID()

        val result = getProductDetailUseCase.execute(slug, sessionId, parsedCorrelationId)
        val product = result.product

        val response = ProductDetailResponse(
            id = product.id,
            slug = product.slug,
            name = product.name,
            description = product.description,
            price = product.price,
            category = product.category,
            tags = product.tags
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList(),
            availability = if (product.status == ProductStatus.PUBLISHED) "IN_STOCK" else "OUT_OF_STOCK",
            relatedProducts = result.relatedProducts.map { p ->
                ProductSummaryResponse(
                    id = p.id,
                    slug = p.slug,
                    name = p.name,
                    price = p.price,
                    category = p.category
                )
            }
        )

        return ResponseEntity.ok(response)
    }
}
