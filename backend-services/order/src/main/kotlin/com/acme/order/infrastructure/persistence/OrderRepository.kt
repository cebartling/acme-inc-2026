package com.acme.order.infrastructure.persistence

import com.acme.order.domain.Order
import com.acme.order.domain.OrderStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

private const val STATUS = "com.acme.order.domain.OrderStatus"

interface OrderRepository : JpaRepository<Order, UUID> {

    /** The order an earlier request with this `Idempotency-Key` placed, if any (AC-5.8). */
    fun findByUserIdAndIdempotencyKey(userId: UUID, idempotencyKey: String): Order?

    /** The user's order in this status (PIN-367); someone else's order is not found. */
    fun findByIdAndUserIdAndStatus(id: UUID, userId: UUID, status: OrderStatus): Order?

    /**
     * The user's orders in this status, newest first, with their unit counts (PIN-367). The
     * ID breaks a tie on `createdAt`; it is a UUID v7, so it is time-ordered too.
     */
    @Query(
        value = "SELECT new com.acme.order.infrastructure.persistence.OrderSummary(" +
            "o.id, o.orderNumber, o.status, o.createdAt, o.subtotal, o.currency, SUM(i.quantity)) " +
            "FROM CustomerOrder o JOIN o.items i " +
            "WHERE o.userId = :userId AND o.status = :status " +
            "GROUP BY o.id, o.orderNumber, o.status, o.createdAt, o.subtotal, o.currency " +
            "ORDER BY o.createdAt DESC, o.id DESC",
        countQuery = "SELECT COUNT(o) FROM CustomerOrder o WHERE o.userId = :userId AND o.status = :status"
    )
    fun findSummaries(
        @Param("userId") userId: UUID,
        @Param("status") status: OrderStatus,
        pageable: Pageable
    ): Page<OrderSummary>

    /**
     * Moves a PENDING order to SUBMITTED. Returns 1 if this call did it, 0 if the order had
     * already left PENDING, so only one of two racing requests writes `OrderCreated`.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE CustomerOrder o SET o.status = $STATUS.SUBMITTED, o.updatedAt = :now " +
            "WHERE o.id = :id AND o.status = $STATUS.PENDING"
    )
    fun markSubmitted(@Param("id") id: UUID, @Param("now") now: Instant): Int

    /** Moves a PENDING order to CANCELLED with the cart's refusal code. Returns 1 or 0. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE CustomerOrder o SET o.status = $STATUS.CANCELLED, o.cancelCode = :code, o.updatedAt = :now " +
            "WHERE o.id = :id AND o.status = $STATUS.PENDING"
    )
    fun markCancelled(@Param("id") id: UUID, @Param("code") code: String, @Param("now") now: Instant): Int
}
