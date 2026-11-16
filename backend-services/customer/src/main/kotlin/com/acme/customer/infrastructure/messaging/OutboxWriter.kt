package com.acme.customer.infrastructure.messaging

import com.acme.customer.domain.events.DomainEvent
import com.acme.customer.infrastructure.persistence.OutboxMessage
import com.acme.customer.infrastructure.persistence.OutboxRepository
import com.acme.customer.infrastructure.projection.CustomerSnapshotFactory
import com.acme.customer.infrastructure.projection.CustomerSnapshotMessage
import com.acme.customer.infrastructure.projection.ReadAfter
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.UUID

/**
 * Writes domain events to the outbox table.
 *
 * This component is responsible for persisting events to the outbox
 * within the same database transaction as domain state changes.
 * Debezium reads the outbox from the write-ahead log and publishes it to Kafka.
 *
 * Each event produces two rows: the event itself, and a snapshot of the customer's full state
 * for the read model's projector. The snapshot goes to its own topic because it holds PII that
 * consumers of `customer.events` must not receive.
 *
 * When called while handling a web request, it also puts the event's id in the response's
 * [ReadAfter.HEADER], so the client can ask a later read to wait for this write.
 *
 * This implements the write side of the Transactional Outbox pattern.
 */
@Component
class OutboxWriter(
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper,
    private val snapshotFactory: CustomerSnapshotFactory
) {
    private val logger = LoggerFactory.getLogger(OutboxWriter::class.java)

    /**
     * Writes a domain event, and the snapshot it produced, to the outbox.
     *
     * Both rows are serialized to JSON and stored with metadata for later publishing.
     * This operation must be called within a transaction, after the domain change is saved,
     * to ensure atomicity with domain changes.
     *
     * @param event The domain event to write to outbox.
     * @param topic The Kafka topic to publish the event to.
     */
    fun write(event: DomainEvent, topic: String) {
        val messageKey = event.aggregateId.toString()

        outboxRepository.save(
            OutboxMessage(
                aggregateId = event.aggregateId,
                aggregateType = event.aggregateType,
                eventType = event.eventType,
                eventId = event.eventId,
                topic = topic,
                messageKey = messageKey,
                payload = objectMapper.writeValueAsString(event)
            )
        )

        writeSnapshot(event.aggregateId, event.aggregateType, event.eventId)

        logger.debug(
            "Wrote {} event {} for aggregate {} to outbox for topic {}, with a snapshot for {}",
            event.eventType,
            event.eventId,
            event.aggregateId,
            topic,
            CustomerSnapshotMessage.TOPIC
        )
    }

    /**
     * Writes a snapshot of the customer's current state for the read model, with no event.
     *
     * [write] uses it for every event. Changes that have no event, such as the test helper's, and
     * a backfill of customers that predate the read model, call it directly.
     *
     * @param sourceEventId The event the snapshot follows; a read that waits for it sees this snapshot.
     * @return [sourceEventId], which the response also carries in [ReadAfter.HEADER].
     */
    fun writeSnapshot(customerId: UUID, aggregateType: String, sourceEventId: UUID = UUID.randomUUID()): UUID {
        val snapshotMessage = CustomerSnapshotMessage(
            sourceEventId = sourceEventId,
            customerId = customerId,
            snapshot = snapshotFactory.build(customerId)
        )
        outboxRepository.save(
            OutboxMessage(
                aggregateId = customerId,
                aggregateType = aggregateType,
                eventType = CustomerSnapshotMessage.EVENT_TYPE,
                eventId = UUID.randomUUID(),
                topic = CustomerSnapshotMessage.TOPIC,
                messageKey = customerId.toString(),
                payload = objectMapper.writeValueAsString(snapshotMessage)
            )
        )
        tellClientToReadAfter(sourceEventId)
        return sourceEventId
    }

    /**
     * Sets the response header on the current web request. The response is not committed yet: this
     * runs inside the use case, before the controller returns. A later event in the same request
     * replaces the header, because its snapshot includes the earlier changes.
     */
    private fun tellClientToReadAfter(eventId: UUID) {
        val response = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.response ?: return
        response.setHeader(ReadAfter.HEADER, eventId.toString())
    }
}
