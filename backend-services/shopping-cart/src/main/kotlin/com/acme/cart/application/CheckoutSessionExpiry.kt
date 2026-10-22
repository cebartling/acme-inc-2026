package com.acme.cart.application

import com.acme.cart.domain.Cart
import com.acme.cart.domain.CheckoutSession
import com.acme.cart.domain.events.CheckoutSessionExpired
import com.acme.cart.domain.events.CheckoutSessionExpiredPayload
import com.acme.cart.infrastructure.messaging.OutboxWriter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Reports a checkout session that lapsed (PIN-330): writes `CheckoutSessionExpired` to the
 * outbox and counts it. It runs inside the transaction that ends the session (PIN-384), so the
 * event commits with it; the count is taken when the event is written, so a write that a lost
 * race rolls back and retries counts twice. Usually the unlock job finds the lapse, but a customer who restarts or leaves
 * checkout before the job unlocks the cart ends the lapsed session first, so those paths report
 * it too: every lapsed session is reported exactly once, by whichever ends it. One that converts
 * into an order (PIN-331) is not an expiry and is not reported.
 */
@Component
class CheckoutSessionExpiry(
    private val outboxWriter: OutboxWriter,
    meterRegistry: MeterRegistry
) {
    private val expiredSessions = meterRegistry.counter(EXPIRED_METRIC)

    fun report(payload: CheckoutSessionExpiredPayload, correlationId: UUID) {
        expiredSessions.increment()
        outboxWriter.write(CheckoutSessionExpired.create(payload, correlationId))
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
