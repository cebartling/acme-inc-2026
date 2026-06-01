package com.acme.product.domain

import jakarta.persistence.*
import org.hibernate.annotations.BatchSize
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "product_variants")
class ProductVariant(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,

    @Column(name = "sku", nullable = false, unique = true, length = 100)
    val sku: String,

    @Column(name = "name", nullable = false, length = 255)
    val name: String,

    @Column(name = "color", length = 100)
    val color: String? = null,

    @Column(name = "size", length = 100)
    val size: String? = null,

    @Column(name = "is_default", nullable = false)
    val isDefault: Boolean = false,

    @Column(name = "in_stock", nullable = false)
    val inStock: Boolean = true,

    @Column(name = "price_override", precision = 10, scale = 2)
    val priceOverride: BigDecimal? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @OneToMany(
        mappedBy = "variant",
        fetch = FetchType.LAZY,
        cascade = [CascadeType.ALL],
        orphanRemoval = true
    )
    @OrderBy("displayOrder ASC")
    @BatchSize(size = 10)
    val images: List<ProductVariantImage> = emptyList(),

    @OneToMany(
        mappedBy = "variant",
        fetch = FetchType.LAZY,
        cascade = [CascadeType.ALL],
        orphanRemoval = true
    )
    @OrderBy("minQuantity ASC")
    @BatchSize(size = 10)
    val tierPricing: List<ProductVariantTierPricing> = emptyList()
)
