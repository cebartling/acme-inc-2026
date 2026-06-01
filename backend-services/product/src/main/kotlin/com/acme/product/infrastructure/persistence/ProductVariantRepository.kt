package com.acme.product.infrastructure.persistence

import com.acme.product.domain.ProductVariant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ProductVariantRepository : JpaRepository<ProductVariant, UUID>
