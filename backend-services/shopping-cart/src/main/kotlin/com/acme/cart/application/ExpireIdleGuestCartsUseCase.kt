package com.acme.cart.application

import com.acme.cart.domain.events.CartExpired
import com.acme.cart.domain.events.CartExpiredPayload
import com.acme.cart.infrastructure.messaging.CartEventPublisher
import com.acme.cart.infrastructure.persistence.CartRepository
import com.acme.cart.infrastructure.persistence.IdleGuestCart
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Expires ACTIVE guest carts that have been idle past the guest TTL (PIN-287), so they
 * stop outliving the session cookie that was the only way to reach them. Rows are kept
 * (Epic 009: soft delete with retention); user and MERGED carts are never touched.
 *
 * Each cart is expired by a conditional UPDATE that re-checks it is still idle, so a cart
 * used between the scan and the update survives, and `CartExpired` is only published for
 * carts this run actually expired. Publishing is best-effort, as for every cart event.
 */
@Service
class ExpireIdleGuestCartsUseCase(
    private val cartRepository: CartRepository,
    private val eventPublisher: CartEventPublisher,
    @Value("\${acme.cart.guest-ttl}") private val guestTtl: Duration,
    meterRegistry: MeterRegistry
) {
    init {
        // A zero or negative TTL would put the cutoff at or after now and expire carts in use.
        require(guestTtl > Duration.ZERO) { "acme.cart.guest-ttl must be positive, was $guestTtl" }
    }

    private val logger = LoggerFactory.getLogger(ExpireIdleGuestCartsUseCase::class.java)
    private val expiredCarts = meterRegistry.counter(EXPIRED_METRIC)

    /** Expires every idle guest cart as of [now] and returns how many. */
    fun execute(now: Instant = Instant.now()): Int {
        // Viewing refreshes activity at most once per interval, so allow that much grace: a
        // cart must never expire while its cookie could still be valid.
        val cutoff = now.minus(guestTtl).minus(ACTIVITY_REFRESH_INTERVAL)
        val correlationId = UUID.randomUUID()
        var total = 0
        while (true) {
            val batch = cartRepository.findIdleGuestCarts(cutoff, PageRequest.of(0, BATCH_SIZE))
            val expired = batch.count { expire(it, cutoff, now, correlationId) }
            total += expired
            // A short batch was the last one; a batch that expired nothing would be found again.
            if (batch.size < BATCH_SIZE || expired == 0) break
        }
        return total
    }

    private fun expire(cart: IdleGuestCart, cutoff: Instant, now: Instant, correlationId: UUID): Boolean {
        if (cartRepository.expireIfIdle(cart.id, cutoff, now) == 0) return false
        expiredCarts.increment()
        eventPublisher.publishLoggingFailure(
            CartExpired.create(
                CartExpiredPayload(cart.id, cart.sessionId, cart.lastActiveAt, cart.lineCount),
                correlationId
            ),
            logger
        )
        return true
    }

    companion object {
        const val BATCH_SIZE = 500

        /** Exposed by Prometheus-style registries as `cart_expired_total`. */
        const val EXPIRED_METRIC = "cart.expired"

        /**
         * A guest viewing their cart refreshes its activity at most this often (see
         * `CartController.getCurrent`), so expiry allows the same grace. One constant keeps both
         * sides in step.
         */
        val ACTIVITY_REFRESH_INTERVAL: Duration = Duration.ofDays(1)
    }
}
