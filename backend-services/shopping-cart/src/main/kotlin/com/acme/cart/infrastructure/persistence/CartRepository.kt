package com.acme.cart.infrastructure.persistence

import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartStatus
import org.springframework.data.jpa.repository.EntityGraph
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import java.util.UUID

/** Lookups load the lines with the cart: every caller reads or changes them. */
interface CartRepository : JpaRepository<Cart, UUID> {
    @EntityGraph(attributePaths = ["items"])
    fun findBySessionIdAndStatus(sessionId: String, status: CartStatus): Cart?

    /**
     * Same as [findBySessionIdAndStatus], but the cart row stays locked until commit, so two
     * concurrent merges of one guest cart run one after the other and the second finds it MERGED.
     *
     * Deliberately no entity graph: with the items join, Hibernate locks in a follow-up
     * statement, so a waiting caller keeps the stale ACTIVE cart it already loaded. Locking
     * the plain row query lets Postgres re-check `status` once the lock is granted. The items
     * load lazily inside the same transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findForUpdateBySessionIdAndStatus(sessionId: String, status: CartStatus): Cart?

    @EntityGraph(attributePaths = ["items"])
    fun findByUserIdAndStatus(userId: UUID, status: CartStatus): Cart?
}
