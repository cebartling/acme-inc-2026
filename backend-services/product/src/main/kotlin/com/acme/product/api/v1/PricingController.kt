package com.acme.product.api.v1

import com.acme.product.domain.VariantNotFoundException
import com.acme.product.infrastructure.projection.CatalogReadModelQueries
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/prices")
class PricingController(
    private val queries: CatalogReadModelQueries
) {

    @GetMapping("/{variantId}")
    fun getPrice(@PathVariable variantId: UUID): ResponseEntity<VariantPriceResponse> {
        val (variant, productPrice) = queries.findVariant(variantId) ?: throw VariantNotFoundException(variantId)

        val override = variant.priceOverride
        val effectivePrice = override ?: productPrice
        val originalPrice = if (override != null && override < productPrice) productPrice else null

        val response = VariantPriceResponse(
            variantId = variantId,
            price = effectivePrice,
            originalPrice = originalPrice,
            tierPricing = variant.tierPricing.map { tp ->
                TierPricingEntry(minQuantity = tp.minQuantity, price = tp.price)
            }
        )
        return ResponseEntity.ok(response)
    }
}
