package com.acme.cart.infrastructure.scheduling

import com.acme.cart.application.ExpireIdleGuestCartsUseCase
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

/**
 * Runs idle guest cart expiry on a fixed delay (PIN-287). `acme.cart.expiry.enabled=false`
 * turns it off, scheduling included; nothing else in this service is scheduled.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = ["acme.cart.expiry.enabled"], havingValue = "true", matchIfMissing = true)
class CartExpiryScheduledTasks(private val expireIdleGuestCarts: ExpireIdleGuestCartsUseCase) {

    private val logger = LoggerFactory.getLogger(CartExpiryScheduledTasks::class.java)

    @Scheduled(
        initialDelayString = "\${acme.cart.expiry.initial-delay}",
        fixedDelayString = "\${acme.cart.expiry.interval}"
    )
    fun expireIdleGuestCarts() {
        val expired = expireIdleGuestCarts.execute()
        if (expired > 0) {
            logger.info("Expired {} idle guest carts", expired)
        }
    }
}
