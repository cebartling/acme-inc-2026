package com.acme.product.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "product_variant_tier_pricing")
class ProductVariantTierPricing(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    val variant: ProductVariant,

    @Column(name = "min_quantity", nullable = false)
    val minQuantity: Int,

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    val price: BigDecimal
)
