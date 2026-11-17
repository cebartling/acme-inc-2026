package com.acme.notification.infrastructure.messaging

import com.acme.notification.domain.NotificationDelivery
import com.acme.notification.domain.events.NotificationSent
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Publishes notification events and read model snapshots (PIN-549, ADR-0068).
 *
 * Both go to the transactional outbox, in the caller's transaction. Debezium's outbox event router
 * publishes the event to `notification.events`, keyed by notification id, and the snapshot to
 * `notification.read-model`. A failed outbox write throws [OutboxWriteException], so nothing is
 * silently dropped.
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
        try {
            outboxWriter.write(event, NotificationSent.TOPIC)
        } catch (e: Exception) {
            throw OutboxWriteException("Could not write ${event.eventType} event ${event.eventId} to the outbox", e)
        }
    }

    /**
     * Writes the delivery's state to the outbox for the read model, with an id of its own because no
     * event caused it. Call it after the delivery's final state is saved.
     */
    fun publishSnapshot(delivery: NotificationDelivery) = publishSnapshot(delivery, UUID.randomUUID())

    /**
     * Writes the delivery's state to the outbox for the read model, following the event [sourceEventId].
     * Call it after the delivery's final state is saved.
     */
    fun publishSnapshot(delivery: NotificationDelivery, sourceEventId: UUID) {
        try {
            outboxWriter.writeSnapshot(delivery, sourceEventId)
        } catch (e: Exception) {
            throw OutboxWriteException("Could not write a snapshot of notification ${delivery.id} to the outbox", e)
        }
    }
}
