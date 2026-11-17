package com.acme.notification.infrastructure.messaging

import com.acme.notification.domain.events.NotificationSent
import org.springframework.stereotype.Component

/**
 * Publishes notification events (PIN-549, ADR-0022).
 *
 * The event goes to the transactional outbox, in the caller's transaction, and Debezium's outbox
 * event router publishes it to `notification.events`, keyed by notification id. A failed outbox
 * write throws, so an event is never silently dropped.
 */
@Component
class NotificationEventPublisher(
    private val outboxWriter: OutboxWriter
) {

    /**
     * Writes a NotificationSent event to the outbox. Call it inside the transaction that saves the delivery.
     *
     * @param event The event to publish.
     */
    fun publish(event: NotificationSent) {
        outboxWriter.write(event, NotificationSent.TOPIC)
    }
}
