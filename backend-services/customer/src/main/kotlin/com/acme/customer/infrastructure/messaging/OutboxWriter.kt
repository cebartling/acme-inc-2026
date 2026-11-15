package com.acme.customer.infrastructure.messaging

import com.acme.customer.domain.events.DomainEvent
import com.acme.customer.infrastructure.persistence.OutboxMessage
import com.acme.customer.infrastructure.persistence.OutboxRepository
import com.acme.customer.infrastructure.projection.CustomerSnapshotFactory
import com.acme.customer.infrastructure.projection.CustomerSnapshotMessage
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
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

        val snapshotMessage = CustomerSnapshotMessage(
            sourceEventId = event.eventId,
            customerId = event.aggregateId,
            snapshot = snapshotFactory.build(event.aggregateId)
        )
        outboxRepository.save(
            OutboxMessage(
                aggregateId = event.aggregateId,
                aggregateType = event.aggregateType,
                eventType = CustomerSnapshotMessage.EVENT_TYPE,
                eventId = UUID.randomUUID(),
                topic = CustomerSnapshotMessage.TOPIC,
                messageKey = messageKey,
                payload = objectMapper.writeValueAsString(snapshotMessage)
            )
        )

        logger.debug(
            "Wrote {} event {} for aggregate {} to outbox for topic {}, with a snapshot for {}",
            event.eventType,
            event.eventId,
            event.aggregateId,
            topic,
            CustomerSnapshotMessage.TOPIC
        )
    }
}
