package com.acme.cart.application

import com.acme.cart.domain.Cart
import com.acme.cart.domain.CheckoutSession
import com.acme.cart.domain.events.CheckoutSessionExpired
import com.acme.cart.domain.events.CheckoutSessionExpiredPayload
import com.acme.cart.infrastructure.messaging.CartEventPublisher
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Reports a checkout session that lapsed (PIN-330): publishes `CheckoutSessionExpired` and
 * counts it. Usually the unlock job finds the lapse, but a customer who restarts or leaves
 * checkout before the job runs ends the lapsed session first, so those paths report it too:
 * every lapsed session is reported exactly once, by whichever ends it.
 */
@Component
class CheckoutSessionExpiry(
    private val eventPublisher: CartEventPublisher,
    meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(CheckoutSessionExpiry::class.java)
    private val expiredSessions = meterRegistry.counter(EXPIRED_METRIC)

    fun report(payload: CheckoutSessionExpiredPayload, correlationId: UUID) {
        expiredSessions.increment()
        eventPublisher.publishLoggingFailure(CheckoutSessionExpired.create(payload, correlationId), logger)
    }

    /** Reports [lapsed], the session [cart] was locked for until the change that ended it. */
    fun report(cart: Cart, lapsed: CheckoutSession, correlationId: UUID) = report(
        CheckoutSessionExpiredPayload(
            cartId = cart.id,
            checkoutSessionId = lapsed.id,
            expiredAt = lapsed.expiresAt,
            lineCount = cart.items.size,
            itemCount = cart.itemCount,
            sessionId = cart.sessionId,
            userId = cart.userId
        ),
        correlationId
    )

    companion object {
        /** Exposed by Prometheus-style registries as `cart_checkout_expired_total`. */
        const val EXPIRED_METRIC = "cart.checkout.expired"
    }
}
