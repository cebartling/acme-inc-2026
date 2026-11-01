package com.acme.order.infrastructure.scheduling

import com.acme.order.application.CancelAbandonedOrdersUseCase
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled

/**
 * Cancels abandoned orders on a fixed delay (PIN-427). `acme.order.abandoned.enabled=false`
 * turns it off.
 */
@Configuration
@ConditionalOnProperty(name = ["acme.order.abandoned.enabled"], havingValue = "true", matchIfMissing = true)
class AbandonedOrderScheduledTasks(private val cancelAbandonedOrders: CancelAbandonedOrdersUseCase) {

    private val logger = LoggerFactory.getLogger(AbandonedOrderScheduledTasks::class.java)

    @Scheduled(
        initialDelayString = "\${acme.order.abandoned.initial-delay}",
        fixedDelayString = "\${acme.order.abandoned.interval}"
    )
    fun cancelAbandonedOrders() {
        val cancelled = cancelAbandonedOrders.execute()
        if (cancelled > 0) {
            logger.info("Cancelled {} abandoned orders", cancelled)
        }
    }
}
