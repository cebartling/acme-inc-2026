package com.acme.cart.application

import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartOwner
import com.acme.cart.domain.CartStatus
import com.acme.cart.domain.events.DomainEvent
import com.acme.cart.infrastructure.messaging.CartEventPublisher
import com.acme.cart.infrastructure.persistence.CartRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import java.sql.SQLException
import java.util.UUID

/** The owner's ACTIVE cart, if any. A MERGED guest cart reads as no cart (AC-0004-08-05). */
internal fun CartRepository.findActiveCart(owner: CartOwner): Cart? = when (owner) {
    is CartOwner.Guest -> findBySessionIdAndStatus(owner.sessionId, CartStatus.ACTIVE)
    is CartOwner.Customer -> findByUserIdAndStatus(owner.userId, CartStatus.ACTIVE)
}

/**
 * The owner's cart, but only if it is the cart the caller named. A `cartId` from the URL
 * that belongs to anyone else resolves to null, the same as a missing cart.
 */
internal fun CartRepository.findOwnedCart(owner: CartOwner, cartId: UUID): Cart? =
    findActiveCart(owner)?.takeIf { it.id == cartId }

/**
 * Publishes after commit, best-effort (no outbox): a Kafka failure is logged and the
 * customer's cart change still succeeds.
 */
internal fun CartEventPublisher.publishLoggingFailure(event: DomainEvent, logger: Logger) {
    try {
        publish(event)
    } catch (ex: Exception) {
        logger.warn("Failed to publish {} event {} for cart {}", event.eventType, event.eventId, event.aggregateId, ex)
    }
}

private val conflictLogger = LoggerFactory.getLogger("com.acme.cart.application.CartConflicts")

/**
 * Runs a cart change, and runs it once more if a concurrent change to the same cart won the
 * race (PIN-278: `Cart` is versioned). [block] must re-read everything it needs, so the retry
 * works on the committed cart: e.g. an update whose line was just removed then fails as
 * [com.acme.cart.domain.CartError.CartItemNotFound]. A second conflict in a row propagates,
 * and the controller answers 409. Events are published after the transaction, so a failed
 * attempt publishes nothing. The session ID is never logged.
 */
internal fun <T> retryOnConflict(operation: String, block: () -> T): T =
    try {
        uniqueKeyRaceAsConflict(block)
    } catch (conflict: ObjectOptimisticLockingFailureException) {
        conflictLogger.info("{} lost a race with a concurrent change to the cart; retrying once", operation)
        uniqueKeyRaceAsConflict(block)
    }

/** Postgres SQLSTATE `unique_violation`. */
private const val UNIQUE_VIOLATION = "23505"

/**
 * Hibernate inserts new rows before it updates the cart's version, so a race over a new row
 * fails on its unique key instead of the version: two first adds that each create the cart
 * (`uq_carts_active_session`/`_user`), or two adds of the same new variant
 * (`uq_cart_items_cart_variant`). Same race, so the same conflict. The violation's message
 * holds the key, which can be the session ID, so it is not copied into the conflict's.
 */
private fun <T> uniqueKeyRaceAsConflict(block: () -> T): T =
    try {
        block()
    } catch (ex: DataIntegrityViolationException) {
        if ((ex.mostSpecificCause as? SQLException)?.sqlState != UNIQUE_VIOLATION) throw ex
        // String?, so it is the (message, cause) constructor, not (className, identifier).
        val message: String? = "A concurrent change to the cart took a unique key first"
        throw ObjectOptimisticLockingFailureException(message, ex)
    }
