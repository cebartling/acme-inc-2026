package com.acme.identity.infrastructure.projection

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
 * Keeps the MongoDB users read model current by applying the snapshots Debezium publishes to
 * `identity.read-model` (PIN-548, ADR-0067).
 *
 * Each message holds the user's full state, so applying it replaces the document. That makes
 * redelivery harmless: Kafka redelivers from the last acknowledged offset in order, so the last
 * snapshot always wins. A message that fails is not acknowledged and is retried by the container
 * (see `KafkaConsumerConfig`: five retries over about 31 seconds). After that it is logged and skipped,
 * so a MongoDB outage longer than that loses the snapshots that arrived during it; a user's document
 * then stays stale until they next change, or until the backfill rewrites it.
 */
@Component
class UserSnapshotProjector(
    private val mongoTemplate: MongoTemplate,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(UserSnapshotProjector::class.java)

    @KafkaListener(
        topics = ["\${identity.events.read-model-topic}"],
        containerFactory = "kafkaListenerContainerFactory"
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

    /** Replaces the user's read model document with the snapshot in [messageJson]. */
    fun apply(messageJson: String) {
        val message = objectMapper.readValue(messageJson, UserSnapshotMessage::class.java)

        val update = Update()
            .set(UserReadModelDocument.EMAIL, message.snapshot.email)
            .set(UserReadModelDocument.SNAPSHOT, Document.parse(objectMapper.writeValueAsString(message.snapshot)))
            .push(UserReadModelDocument.APPLIED_EVENT_IDS)
            .slice(-UserReadModelDocument.MAX_APPLIED_EVENT_IDS)
            .each(message.sourceEventId.toString())

        mongoTemplate.upsert(
            Query(Criteria.where(UserReadModelDocument.ID).`is`(message.userId.toString())),
            update,
            UserReadModelDocument.COLLECTION
        )

        logger.info("Projected user {} from event {} to the MongoDB read model", message.userId, message.sourceEventId)
    }
}
