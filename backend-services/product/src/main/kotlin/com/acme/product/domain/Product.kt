package com.acme.product.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Lifecycle status of a product in the catalog.
 */
enum class ProductStatus {
    PUBLISHED,
    ARCHIVED
}

/**
 * JPA entity representing a product in the ACME catalog.
 *
 * @property id Unique identifier for the product (UUID).
 * @property slug URL-friendly identifier.
 * @property name Display name of the product.
 * @property description Full product description.
 * @property price Price in the default currency.
 * @property status Lifecycle status; only PUBLISHED products appear in search results.
 * @property category Optional product category.
 * @property tags Comma-separated tags for categorization.
 * @property createdAt Timestamp when the record was created.
 * @property updatedAt Timestamp when the record was last modified.
 */
@Entity
@Table(name = "products")
class Product(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "slug", nullable = false, unique = true, length = 255)
    val slug: String,

    @Column(name = "name", nullable = false, length = 500)
    val name: String,

    @Column(name = "description", columnDefinition = "TEXT")
    val description: String? = null,

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    val price: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    val status: ProductStatus = ProductStatus.PUBLISHED,

    @Column(name = "category", length = 255)
    val category: String? = null,

    @Column(name = "tags", length = 1000)
    val tags: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
)
