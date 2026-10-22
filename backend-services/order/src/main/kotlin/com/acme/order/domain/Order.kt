package com.acme.order.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Where an order is in placement (PIN-366). It is saved PENDING, becomes SUBMITTED once
 * the cart is converted (and only then is `OrderCreated` written), or CANCELLED when the
 * cart refuses the conversion for good.
 */
enum class OrderStatus { PENDING, SUBMITTED, CANCELLED }

/**
 * A customer's order, copied from their checkout session's cart (journey 0005 step 5).
 *
 * `(customerId, idempotencyKey)` is unique, so a repeated `POST /api/v1/orders` with the
 * same `Idempotency-Key` finds this order instead of creating another (AC-5.8). Status
 * changes go through `OrderRepository.markSubmitted` / `markCancelled`, conditional updates,
 * so two racing requests can't both submit the order.
 */
// ORDER is a JPQL keyword, so queries name the entity CustomerOrder
@Entity(name = "CustomerOrder")
@Table(name = "orders")
class Order(
    @Id
    val id: UUID,

    @Column(name = "order_number", nullable = false, length = 20)
    val orderNumber: String,

    @Column(name = "customer_id", nullable = false)
    val customerId: UUID,

    @Column(name = "checkout_session_id", nullable = false)
    val checkoutSessionId: UUID,

    @Column(name = "cart_id", nullable = false)
    val cartId: UUID,

    @Column(name = "idempotency_key", nullable = false, length = 255)
    val idempotencyKey: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    val status: OrderStatus,

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    val subtotal: BigDecimal,

    @Column(name = "currency", nullable = false, length = 3)
    val currency: String,

    @Column(name = "cancel_code", length = 50)
    val cancelCode: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = createdAt
) {
    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val items: MutableList<OrderItem> = mutableListOf()

    fun addItem(
        id: UUID,
        variantId: UUID,
        quantity: Int,
        unitPrice: BigDecimal,
        lineTotal: BigDecimal,
        productSnapshot: String
    ) {
        items.add(OrderItem(id, this, variantId, quantity, unitPrice, lineTotal, productSnapshot))
    }
}

/** One cart line, copied into the order. `productSnapshot` is the cart's snapshot, as JSON. */
@Entity
@Table(name = "order_items")
class OrderItem(
    @Id
    val id: UUID,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    val order: Order,

    @Column(name = "variant_id", nullable = false)
    val variantId: UUID,

    @Column(name = "quantity", nullable = false)
    val quantity: Int,

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    val unitPrice: BigDecimal,

    @Column(name = "line_total", nullable = false, precision = 10, scale = 2)
    val lineTotal: BigDecimal,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "product_snapshot", nullable = false, columnDefinition = "jsonb")
    val productSnapshot: String
)
