package com.acme.cart.infrastructure.projection

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * Keeps the MongoDB read model current by applying the snapshots Debezium publishes to
 * `cart.read-model` (PIN-551).
 *
 * Each message holds the cart's full state, so applying it replaces the document. Redelivery
 * is safe once it catches up: Kafka redelivers from the last acknowledged offset in order, so
 * the last snapshot wins in the end. Until then an older snapshot can overwrite a newer one while
 * the newer event id is still in `appliedEventIds`, so a read can briefly see older state.
 * A snapshot with no cart means the cart was purged, and deletes it. A message that fails is not
 * acknowledged and is retried by the container.
 */
@Component
class CartSnapshotProjector(
    private val mongoTemplate: MongoTemplate,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(CartSnapshotProjector::class.java)

    @KafkaListener(topics = [CartSnapshotMessage.TOPIC])
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

    /** Replaces the cart's read model document with the snapshot in [messageJson]. */
    fun apply(messageJson: String) {
        val message = objectMapper.readValue(messageJson, CartSnapshotMessage::class.java)
        val byId = Query(Criteria.where(CartReadModelDocument.ID).`is`(message.cartId.toString()))
        val cart = message.cart
        if (cart == null) {
            mongoTemplate.remove(byId, CartReadModelDocument.COLLECTION)
            logger.info("Removed purged cart {} from the MongoDB read model (event {})", message.cartId, message.sourceEventId)
            return
        }

        val update = Update()
            .set(CartReadModelDocument.USER_ID, message.userId?.toString())
            .set(CartReadModelDocument.SESSION_ID, message.sessionId)
            .set(CartReadModelDocument.STATUS, cart.status.name)
            .set(CartReadModelDocument.CART_JSON, objectMapper.writeValueAsString(cart))
            .push(CartReadModelDocument.APPLIED_EVENT_IDS)
            .slice(-CartReadModelDocument.MAX_APPLIED_EVENT_IDS)
            .each(message.sourceEventId.toString())
        mongoTemplate.upsert(byId, update, CartReadModelDocument.COLLECTION)
        logger.info("Projected cart {} from event {} to the MongoDB read model", message.cartId, message.sourceEventId)
    }
}
