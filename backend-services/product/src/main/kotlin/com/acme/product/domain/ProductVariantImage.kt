package com.acme.product.domain

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "product_variant_images")
class ProductVariantImage(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    val variant: ProductVariant,

    @Column(name = "url", nullable = false)
    val url: String,

    @Column(name = "display_order", nullable = false)
    val displayOrder: Int = 0
)
