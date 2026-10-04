package com.acme.cart.infrastructure.scheduling

import com.acme.cart.application.UnlockLapsedCheckoutsUseCase
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

/**
 * Unlocks carts whose checkout session lapsed on a fixed delay (PIN-330).
 * `acme.cart.checkout-unlock.enabled=false` turns it off; the other cart jobs have their own switches.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = ["acme.cart.checkout-unlock.enabled"], havingValue = "true", matchIfMissing = true)
class CartCheckoutUnlockScheduledTasks(private val unlockLapsedCheckouts: UnlockLapsedCheckoutsUseCase) {

    private val logger = LoggerFactory.getLogger(CartCheckoutUnlockScheduledTasks::class.java)

    @Scheduled(
        initialDelayString = "\${acme.cart.checkout-unlock.initial-delay}",
        fixedDelayString = "\${acme.cart.checkout-unlock.interval}"
    )
    fun unlockLapsedCheckouts() {
        val unlocked = unlockLapsedCheckouts.execute()
        if (unlocked > 0) {
            logger.info("Unlocked {} carts whose checkout session lapsed", unlocked)
        }
    }
}
