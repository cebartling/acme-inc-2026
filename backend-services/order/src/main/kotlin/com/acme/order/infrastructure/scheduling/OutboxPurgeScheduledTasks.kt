package com.acme.order.infrastructure.scheduling

import com.acme.order.application.PurgeOutboxUseCase
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled

/**
 * Deletes old outbox rows on a fixed delay (PIN-552). `acme.order.outbox.retention.enabled=false`
 * turns it off.
 */
@Configuration
@ConditionalOnProperty(name = ["acme.order.outbox.retention.enabled"], havingValue = "true", matchIfMissing = true)
class OutboxPurgeScheduledTasks(private val purgeOutbox: PurgeOutboxUseCase) {

    private val logger = LoggerFactory.getLogger(OutboxPurgeScheduledTasks::class.java)

    @Scheduled(
        initialDelayString = "\${acme.order.outbox.retention.initial-delay}",
        fixedDelayString = "\${acme.order.outbox.retention.interval}"
    )
    fun purgeOutbox() {
        val purged = purgeOutbox.execute()
        if (purged > 0) {
            logger.info("Purged {} outbox rows", purged)
        }
    }
}
