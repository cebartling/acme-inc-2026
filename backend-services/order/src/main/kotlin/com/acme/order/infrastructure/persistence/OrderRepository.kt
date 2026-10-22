package com.acme.order.infrastructure.persistence

import com.acme.order.domain.Order
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

private const val STATUS = "com.acme.order.domain.OrderStatus"

interface OrderRepository : JpaRepository<Order, UUID> {

    /** The order an earlier request with this `Idempotency-Key` placed, if any (AC-5.8). */
    fun findByCustomerIdAndIdempotencyKey(customerId: UUID, idempotencyKey: String): Order?

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
