package com.acme.cart.application

import com.acme.cart.domain.Cart
import com.acme.cart.domain.events.DomainEvent
import com.acme.cart.infrastructure.messaging.CartEventPublisher
import com.acme.cart.infrastructure.persistence.CartRepository
import org.slf4j.Logger
import java.util.UUID

/**
 * The session's cart, but only if it is the cart the caller named. A `cartId` from the
 * URL that belongs to another session resolves to null, the same as a missing cart.
 */
internal fun CartRepository.findOwnedCart(sessionId: String, cartId: UUID): Cart? =
    findBySessionId(sessionId)?.takeIf { it.id == cartId }

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
