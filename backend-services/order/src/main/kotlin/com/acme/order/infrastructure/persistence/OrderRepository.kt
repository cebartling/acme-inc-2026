package com.acme.order.infrastructure.persistence

import com.acme.order.domain.Order
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

private const val STATUS = "com.acme.order.domain.OrderStatus"
private const val CANCELLED_BY = "com.acme.order.domain.CancelledBy"

/** What a customer sees: their placed orders, and those they cancelled (ADR-0063). */
private const val VISIBLE =
    "(o.status = $STATUS.SUBMITTED OR (o.status = $STATUS.CANCELLED AND o.cancelledBy = $CANCELLED_BY.CUSTOMER))"

interface OrderRepository : JpaRepository<Order, UUID> {

    /** The order an earlier request with this `Idempotency-Key` placed, if any (AC-5.8). */
    fun findByUserIdAndIdempotencyKey(userId: UUID, idempotencyKey: String): Order?

    /** The user's order, if they can see it (PIN-367, PIN-429); someone else's is not found. */
    @Query("SELECT o FROM CustomerOrder o WHERE o.id = :id AND o.userId = :userId AND $VISIBLE")
    fun findVisible(@Param("id") id: UUID, @Param("userId") userId: UUID): Order?

    /**
     * The orders the user can see, newest first, with their unit counts (PIN-367). The ID
     * breaks a tie on `createdAt`; it is a UUID v7, so it is time-ordered too.
     */
    @Query(
        value = "SELECT new com.acme.order.infrastructure.persistence.OrderSummary(" +
            "o.id, o.orderNumber, o.status, o.createdAt, o.subtotal, o.currency, SUM(i.quantity)) " +
            "FROM CustomerOrder o JOIN o.items i " +
            "WHERE o.userId = :userId AND $VISIBLE " +
            "GROUP BY o.id, o.orderNumber, o.status, o.createdAt, o.subtotal, o.currency " +
            "ORDER BY o.createdAt DESC, o.id DESC",
        countQuery = "SELECT COUNT(o) FROM CustomerOrder o WHERE o.userId = :userId AND $VISIBLE"
    )
    fun findSummaries(@Param("userId") userId: UUID, pageable: Pageable): Page<OrderSummary>

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

    /**
     * Moves a PENDING order to CANCELLED with the code saying why: the cart's refusal, or the
     * abandoned order job's (PIN-427). Returns 1 or 0.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE CustomerOrder o SET o.status = $STATUS.CANCELLED, o.cancelCode = :code, " +
            "o.cancelledBy = $CANCELLED_BY.PLACEMENT, o.updatedAt = :now " +
            "WHERE o.id = :id AND o.status = $STATUS.PENDING"
    )
    fun markCancelled(@Param("id") id: UUID, @Param("code") code: String, @Param("now") now: Instant): Int

    /**
     * Moves the user's SUBMITTED order to CANCELLED (PIN-429). Returns 1 if this call did it, 0
     * otherwise, so only one of two racing requests writes `OrderCancelled`.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE CustomerOrder o SET o.status = $STATUS.CANCELLED, o.cancelledBy = $CANCELLED_BY.CUSTOMER, " +
            "o.cancelReason = :reason, o.updatedAt = :now " +
            "WHERE o.id = :id AND o.userId = :userId AND o.status = $STATUS.SUBMITTED"
    )
    fun markCancelledByCustomer(
        @Param("id") id: UUID,
        @Param("userId") userId: UUID,
        @Param("reason") reason: String,
        @Param("now") now: Instant
    ): Int

    /** PENDING orders created before [cutoff], oldest first (PIN-427). */
    @Query(
        "SELECT o FROM CustomerOrder o " +
            "WHERE o.status = $STATUS.PENDING AND o.createdAt < :cutoff " +
            "ORDER BY o.createdAt, o.id"
    )
    fun findAbandoned(@Param("cutoff") cutoff: Instant, pageable: Pageable): List<Order>
}
