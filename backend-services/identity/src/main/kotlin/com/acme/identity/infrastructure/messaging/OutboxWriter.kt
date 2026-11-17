package com.acme.identity.infrastructure.messaging

import com.acme.identity.domain.events.DomainEvent
import com.acme.identity.infrastructure.persistence.OutboxMessage
import com.acme.identity.infrastructure.persistence.OutboxRepository
import com.acme.identity.infrastructure.projection.UserSnapshotFactory
import com.acme.identity.infrastructure.projection.UserSnapshotMessage
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Writes an identity event to the transactional outbox (PIN-548, ADR-0022), for Debezium's
 * outbox event router to publish to the event's topic, keyed by aggregate id.
 *
 * It joins the caller's transaction, so the event commits or rolls back with the change. Session,
 * device and token-reuse events describe state in Redis and are written outside any transaction;
 * the write then opens its own, so the event is saved but is not atomic with the Redis change.
 *
 * An event that changes a user's state is written with [writeWithSnapshot], which adds a snapshot
 * of the user for the read model's projector. The snapshot goes to its own topic, so consumers of
 * the `identity.*` topics never see it.
 */
@Component
class OutboxWriter(
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper,
    private val snapshotFactory: UserSnapshotFactory
) {
    private val logger = LoggerFactory.getLogger(OutboxWriter::class.java)

    @Transactional
    fun write(event: DomainEvent, topic: String) {
        outboxRepository.save(
            OutboxMessage(
                aggregateId = event.aggregateId,
                aggregateType = event.aggregateType,
                eventType = event.eventType,
                eventId = event.eventId,
                topic = topic,
                messageKey = event.aggregateId.toString(),
                payload = objectMapper.writeValueAsString(event)
            )
        )

        logger.debug(
            "Wrote {} event {} for aggregate {} to the outbox for topic {}",
            event.eventType, event.eventId, event.aggregateId, topic
        )
    }

    /**
     * Writes [event], then a snapshot of the user it changed. Call it after the change is saved.
     * The event's aggregate must be the user.
     */
    @Transactional
    fun writeWithSnapshot(event: DomainEvent, topic: String) {
        write(event, topic)
        writeSnapshot(event.aggregateId, event.aggregateType, event.eventId)
    }

    /**
     * Writes a snapshot of the user's current state for the read model, with no event: for changes
     * that have none, such as the test helper's, and the backfill of users that predate the read model.
     *
     * @param sourceEventId The event the snapshot follows.
     */
    @Transactional
    fun writeSnapshot(userId: UUID, aggregateType: String, sourceEventId: UUID = UUID.randomUUID()) {
        val message = UserSnapshotMessage(
            sourceEventId = sourceEventId,
            userId = userId,
            snapshot = snapshotFactory.build(userId)
        )
        outboxRepository.save(
            OutboxMessage(
                aggregateId = userId,
                aggregateType = aggregateType,
                eventType = UserSnapshotMessage.EVENT_TYPE,
                eventId = UUID.randomUUID(),
                topic = UserSnapshotMessage.TOPIC,
                messageKey = userId.toString(),
                payload = objectMapper.writeValueAsString(message)
            )
        )
    }
}
