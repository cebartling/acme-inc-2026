package com.acme.notification.infrastructure.projection

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * Keeps the MongoDB delivery read model current by applying the snapshots Debezium publishes to
 * `notification.read-model` (PIN-549, ADR-0068).
 *
 * Each message holds the delivery's full state, so applying it replaces the document. That makes
 * redelivery harmless: Kafka redelivers from the last acknowledged offset in order, so the last
 * snapshot always wins. A message that fails is not acknowledged and is retried by the container
 * (see `ReadModelKafkaConfig`: five retries over about 31 seconds). After that it is logged and
 * skipped, so a MongoDB outage longer than that loses the snapshots that arrived during it; the
 * document then stays stale until the delivery next changes, or until the backfill rewrites it.
 */
@Component
class DeliverySnapshotProjector(
    private val mongoTemplate: MongoTemplate,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(DeliverySnapshotProjector::class.java)

    @KafkaListener(
        topics = ["\${notification.events.read-model-topic}"],
        containerFactory = "readModelListenerContainerFactory"
    )
    fun consume(record: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        try {
            apply(record.value())
            acknowledgment.acknowledge()
        } catch (e: Exception) {
            logger.error(
                "Failed to project snapshot from partition {} offset {}: {}",
                record.partition(), record.offset(), e.message, e
            )
            throw e
        }
    }

    /** Replaces the delivery's read model document with the snapshot in [messageJson]. */
    fun apply(messageJson: String) {
        val message = objectMapper.readValue(messageJson, DeliverySnapshotMessage::class.java)
        val snapshot = message.snapshot

        val update = Update()
            .set(NotificationReadModelDocument.RECIPIENT_ID, snapshot.recipientId.toString())
            .set(NotificationReadModelDocument.RECIPIENT_EMAIL, snapshot.recipientEmail)
            .set(NotificationReadModelDocument.CREATED_AT, snapshot.createdAt.toString())
            .set(NotificationReadModelDocument.SNAPSHOT, Document.parse(objectMapper.writeValueAsString(snapshot)))
            .push(NotificationReadModelDocument.APPLIED_EVENT_IDS)
            .slice(-NotificationReadModelDocument.MAX_APPLIED_EVENT_IDS)
            .each(message.sourceEventId.toString())

        mongoTemplate.upsert(
            Query(Criteria.where(NotificationReadModelDocument.ID).`is`(message.notificationId.toString())),
            update,
            NotificationReadModelDocument.COLLECTION
        )

        logger.info(
            "Projected notification {} from event {} to the MongoDB read model",
            message.notificationId, message.sourceEventId
        )
    }
}
