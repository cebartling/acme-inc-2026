package com.acme.product.api.v1

import com.acme.product.domain.VariantNotFoundException
import com.acme.product.infrastructure.persistence.ProductVariantRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/inventory")
class InventoryController(
    private val variantRepository: ProductVariantRepository
) {

    @GetMapping("/availability/{variantId}")
    fun getAvailability(@PathVariable variantId: UUID): ResponseEntity<VariantAvailabilityResponse> {
        val variant = variantRepository.findById(variantId)
            .orElseThrow { VariantNotFoundException(variantId) }

        val availability = if (variant.inStock) "IN_STOCK" else "OUT_OF_STOCK"
        return ResponseEntity.ok(VariantAvailabilityResponse(variantId = variantId, availability = availability))
    }
}
