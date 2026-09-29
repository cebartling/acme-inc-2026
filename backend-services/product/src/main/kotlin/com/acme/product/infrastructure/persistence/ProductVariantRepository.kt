package com.acme.product.infrastructure.persistence

import com.acme.product.domain.ProductStatus
import com.acme.product.domain.ProductVariant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface ProductVariantRepository : JpaRepository<ProductVariant, UUID> {

    /**
     * A variant whose product has [status]; the price and availability lookups pass PUBLISHED,
     * so an archived product's variant is not found, like the product itself (PIN-306).
     */
    fun findByIdAndProductStatus(id: UUID, status: ProductStatus): Optional<ProductVariant>
}
