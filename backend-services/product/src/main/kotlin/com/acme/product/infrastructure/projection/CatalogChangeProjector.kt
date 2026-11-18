package com.acme.product.infrastructure.projection

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * Keeps the MongoDB catalog read model current from the row changes Debezium publishes for product's
 * four catalog tables, `product.public.<table>` (PIN-550).
 *
 * Each message is applied to the table's mirror collection, and then the affected product's view
 * document is rebuilt from the mirrors ([CatalogViewRebuilder]). A message other than a delete holds
 * the row's full state, so applying it replaces the mirror document, and a rebuild reads the mirrors
 * as they are: redelivery and out-of-order arrival across the four topics both converge on the same
 * result. A delete holds only the row's id, so the owning product comes from the stored mirror row.
 *
 * A message that fails is not acknowledged and is retried by the container (see
 * `CatalogKafkaConfig`: five retries over about 31 seconds; an invalid message is not retried). After
 * that it is logged and skipped, so a MongoDB outage longer than that leaves the view stale for the
 * rows that changed during it, until each of them changes again or the connector's snapshot is
 * re-run.
 */
@Component
class CatalogChangeProjector(
    private val mongoTemplate: MongoTemplate,
    private val objectMapper: ObjectMapper,
    private val rebuilder: CatalogViewRebuilder
) {
    private val logger = LoggerFactory.getLogger(CatalogChangeProjector::class.java)

    private val mirrors = mapOf(
        "products" to CatalogReadModel.PRODUCTS,
        "product_variants" to CatalogReadModel.VARIANTS,
        "product_variant_images" to CatalogReadModel.IMAGES,
        "product_variant_tier_pricing" to CatalogReadModel.TIER_PRICING
    )

    @KafkaListener(
        topics = [
            "\${product.catalog.topic-prefix}products",
            "\${product.catalog.topic-prefix}product_variants",
            "\${product.catalog.topic-prefix}product_variant_images",
            "\${product.catalog.topic-prefix}product_variant_tier_pricing"
        ],
        containerFactory = "catalogListenerContainerFactory"
    )
    fun consume(record: ConsumerRecord<String, String?>, acknowledgment: Acknowledgment) {
        try {
            // Debezium is configured not to publish tombstones (tombstones.on.delete: false)
            val value = record.value()
            if (value == null) {
                logger.warn("Skipping a null value on {} partition {} offset {}", record.topic(), record.partition(), record.offset())
            } else {
                apply(record.topic().substringAfterLast('.'), value)
            }
            acknowledgment.acknowledge()
        } catch (e: Exception) {
            logger.error(
                "Failed to project {} partition {} offset {}: {}",
                record.topic(), record.partition(), record.offset(), e.message, e
            )
            throw e
        }
    }

    /** Applies one row change from [table] (the source table's name) to the read model. */
    fun apply(table: String, messageJson: String) {
        val collection = mirrors[table] ?: throw IllegalArgumentException("Unknown catalog table '$table'")
        val row = objectMapper.readTree(messageJson) as? ObjectNode
            ?: throw IllegalArgumentException("The message for '$table' is not a JSON object")
        val id = row.get("id")?.asText()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("The message for '$table' has no id")
        val deleted = row.get("__deleted")?.asText() == "true"

        // The mirror's document id is the row's id; Debezium's own fields are not kept
        row.remove(listOf("id", "__op", "__source_ts_ms", "__deleted"))

        // A delete carries only the row's id (the tables use the default replica identity) and blanks
        // in every other column, so the owning product is read from the row as it was stored
        val source = if (deleted) {
            mongoTemplate.findOne(Query(Criteria.where("_id").`is`(id)), Document::class.java, collection)
                .also { mongoTemplate.remove(Query(Criteria.where("_id").`is`(id)), collection) }
        } else {
            Document.parse(objectMapper.writeValueAsString(row)).append("_id", id)
                .also { mongoTemplate.save(it, collection) }
        }

        // A product is its own id even when its row is already gone, so a retried product delete still
        // removes the view; a child that is not stored has no product to find
        val productId = if (table == "products") id else source?.let { productIdOf(table, id, it) }
        if (productId == null) {
            logger.debug("No product to rebuild for {} {}: its variant, or the row itself, is not stored", table, id)
        } else {
            rebuilder.rebuild(productId)
            logger.info("Projected {} {} to the catalog read model (product {})", table, id, productId)
        }
    }

    /**
     * The product a row belongs to. An image or tier price whose variant has not arrived has none
     * yet: the variant's own message rebuilds the product with it.
     */
    private fun productIdOf(table: String, id: String, row: Document): String? = when (table) {
        "products" -> id
        "product_variants" -> row.getString("product_id") ?: throw IllegalArgumentException("The variant $id has no product_id")
        else -> {
            val variantId = row.getString("variant_id") ?: throw IllegalArgumentException("The $table row $id has no variant_id")
            mongoTemplate.findOne(Query(Criteria.where("_id").`is`(variantId)), Document::class.java, CatalogReadModel.VARIANTS)
                ?.getString("product_id")
        }
    }
}
