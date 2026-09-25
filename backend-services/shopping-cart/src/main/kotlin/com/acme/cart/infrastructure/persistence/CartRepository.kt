package com.acme.cart.infrastructure.persistence

import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartStatus
import org.springframework.data.jpa.repository.EntityGraph
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.domain.Pageable
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
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

    // --- PIN-287: idle guest carts expire ---------------------------------------------

    /**
     * Marks the session's ACTIVE cart as in use, but only if its activity is older than
     * [staleBefore], so a guest's page views write at most once a day. Returns 1 if it did.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """UPDATE Cart c SET c.lastActiveAt = :now
           WHERE c.sessionId = :sessionId AND c.status = com.acme.cart.domain.CartStatus.ACTIVE
             AND c.lastActiveAt < :staleBefore"""
    )
    fun touchGuestCart(sessionId: String, now: Instant, staleBefore: Instant): Int

    /**
     * ACTIVE guest carts idle since before [cutoff], oldest first, with what a `CartExpired`
     * event needs, so expiring them never loads a cart or its lines.
     */
    @Query(
        """SELECT new com.acme.cart.infrastructure.persistence.IdleGuestCart(
               c.id, c.sessionId, c.lastActiveAt, SIZE(c.items))
           FROM Cart c
           WHERE c.sessionId IS NOT NULL AND c.status = com.acme.cart.domain.CartStatus.ACTIVE
             AND c.lastActiveAt < :cutoff
           ORDER BY c.lastActiveAt ASC"""
    )
    fun findIdleGuestCarts(cutoff: Instant, page: Pageable): List<IdleGuestCart>

    /**
     * Expires one cart if it is still an idle ACTIVE guest cart. The conditions are re-checked
     * in the UPDATE itself, so a cart that saw activity after it was found is left alone, and
     * two runs never expire the same cart twice. Returns 1 if this call expired it.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """UPDATE Cart c SET c.status = com.acme.cart.domain.CartStatus.EXPIRED, c.updatedAt = :now
           WHERE c.id = :id AND c.sessionId IS NOT NULL
             AND c.status = com.acme.cart.domain.CartStatus.ACTIVE AND c.lastActiveAt < :cutoff"""
    )
    fun expireIfIdle(id: UUID, cutoff: Instant, now: Instant): Int
}
