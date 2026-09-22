package com.acme.cart.domain

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "cart_items")
class CartItem(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    val cart: Cart,

    @Column(name = "variant_id", nullable = false)
    val variantId: UUID,

    @Column(name = "quantity", nullable = false)
    var quantity: Int,

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    var unitPrice: BigDecimal,

    /** Product details frozen at the time of add, stored as a JSON object. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "product_snapshot", nullable = false)
    val productSnapshot: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt
) {
    val lineTotal: BigDecimal
        get() = unitPrice.multiply(quantity.toBigDecimal())
}
