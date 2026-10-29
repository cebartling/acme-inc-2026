package com.acme.cart.api.v1

import com.acme.cart.infrastructure.persistence.CartRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/**
 * Test helpers for acceptance tests (PIN-426), loaded only with the "test" profile, as the
 * customer service's are.
 *
 * SECURITY: This controller MUST NOT be enabled in production.
 */
@RestController
@RequestMapping("/api/v1/test")
@Profile("test")
class TestHelperController(private val cartRepository: CartRepository) {

    private val logger = LoggerFactory.getLogger(TestHelperController::class.java)

    /**
     * Lapses a live checkout session (journey 0005 E6) by moving its expiry a minute into the
     * past. The cart stays locked until the unlock job's grace period has passed, but the
     * session is no longer live: reading it, and so placing an order from it, is 404.
     */
    @PostMapping("/checkout-sessions/{checkoutSessionId}/lapse")
    fun lapseCheckoutSession(@PathVariable checkoutSessionId: UUID): ResponseEntity<Any> {
        val lapsed = cartRepository.lapseCheckoutSession(checkoutSessionId, Instant.now().minusSeconds(60))
        if (lapsed == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                mapOf("error" to "Checkout session not found", "code" to "CHECKOUT_SESSION_NOT_FOUND")
            )
        }
        logger.info("Lapsed checkout session {} for a test", checkoutSessionId)
        return ResponseEntity.noContent().build()
    }
}
