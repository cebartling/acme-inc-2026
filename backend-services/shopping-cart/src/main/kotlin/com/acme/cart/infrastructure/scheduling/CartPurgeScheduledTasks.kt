package com.acme.cart.infrastructure.scheduling

import com.acme.cart.application.PurgeFinalCartsUseCase
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

/**
 * Runs the purge of EXPIRED and MERGED carts past retention on a fixed delay (PIN-289).
 * `acme.cart.purge.enabled=false` turns it off; the expiry job has its own switch.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = ["acme.cart.purge.enabled"], havingValue = "true", matchIfMissing = true)
class CartPurgeScheduledTasks(private val purgeFinalCarts: PurgeFinalCartsUseCase) {

    private val logger = LoggerFactory.getLogger(CartPurgeScheduledTasks::class.java)

    @Scheduled(
        initialDelayString = "\${acme.cart.purge.initial-delay}",
        fixedDelayString = "\${acme.cart.purge.interval}"
    )
    fun purgeFinalCarts() {
        val purged = purgeFinalCarts.execute()
        if (purged > 0) {
            logger.info("Purged {} expired and merged carts", purged)
        }
    }
}
