package com.acme.customer.infrastructure.projection

import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
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
import java.util.concurrent.TimeUnit

/**
 * Keeps the MongoDB read model current by applying the snapshots Debezium publishes to
 * `customer.read-model`.
 *
 * Each message holds the customer's full state, so applying it replaces the document. That makes
 * redelivery harmless: Kafka redelivers from the last acknowledged offset in order, so the last
 * snapshot always wins. A message that fails is not acknowledged and is retried by the container.
 */
@Component
class CustomerSnapshotProjector(
    private val mongoTemplate: MongoTemplate,
    private val objectMapper: ObjectMapper,
    meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(CustomerSnapshotProjector::class.java)

    private val projectionTimer: Timer = Timer.builder("customer.read_model.projection.duration")
        .register(meterRegistry)

    @KafkaListener(
        topics = ["\${customer.events.read-model-topic}"],
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun consume(record: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        try {
            apply(record.value())
            acknowledgment.acknowledge()
        } catch (e: Exception) {
            logger.error(
                "Failed to project snapshot from partition {} offset {}: {}",
                record.partition(),
                record.offset(),
                e.message,
                e
            )
            throw e
        }
    }

    /** Replaces the customer's read model document with the snapshot in [messageJson]. */
    fun apply(messageJson: String) {
        val startTime = System.nanoTime()
        val message = objectMapper.readValue(messageJson, CustomerSnapshotMessage::class.java)
        val snapshot = message.snapshot

        val update = Update()
            .set(CustomerReadModelDocument.USER_ID, snapshot.customer.userId)
            .set(CustomerReadModelDocument.CUSTOMER_NUMBER, snapshot.customer.customerNumber)
            .set(CustomerReadModelDocument.EMAIL, snapshot.customer.email.address)
            .set(CustomerReadModelDocument.SNAPSHOT, Document.parse(objectMapper.writeValueAsString(snapshot)))
            .push(CustomerReadModelDocument.APPLIED_EVENT_IDS)
            .slice(-CustomerReadModelDocument.MAX_APPLIED_EVENT_IDS)
            .each(message.sourceEventId.toString())

        mongoTemplate.upsert(
            Query(Criteria.where(CustomerReadModelDocument.ID).`is`(message.customerId.toString())),
            update,
            CustomerReadModelDocument.COLLECTION
        )

        projectionTimer.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS)
        logger.info(
            "Projected customer {} from event {} to the MongoDB read model",
            message.customerId,
            message.sourceEventId
        )
    }
}
