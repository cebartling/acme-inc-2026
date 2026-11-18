package com.acme.product.infrastructure.projection

import jakarta.annotation.PostConstruct
import org.bson.Document
import org.bson.types.Decimal128
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant
import java.util.Date

/**
 * Builds a product's [CatalogReadModel.VIEW] document from the mirror collections.
 *
 * A product that is missing from the mirrors has no view document, so a variant that arrives before
 * its product is held in its mirror until the product arrives. Stock and the card image follow the
 * rules the SQL used: a product is in stock when it has no variants or any variant is in stock, and
 * its image is the first by display order of the product's first variant (default first, then
 * oldest).
 */
@Component
class CatalogViewRebuilder(
    private val mongoTemplate: MongoTemplate
) {

    @PostConstruct
    fun ensureIndexes() {
        mongoTemplate.indexOps(CatalogReadModel.VARIANTS).ensureIndex(Index().on("product_id", Sort.Direction.ASC))
        mongoTemplate.indexOps(CatalogReadModel.IMAGES).ensureIndex(Index().on("variant_id", Sort.Direction.ASC))
        mongoTemplate.indexOps(CatalogReadModel.TIER_PRICING).ensureIndex(Index().on("variant_id", Sort.Direction.ASC))
    }

    fun rebuild(productId: String) {
        val product = find(CatalogReadModel.PRODUCTS, Criteria.where("_id").`is`(productId)).firstOrNull()
        if (product == null) {
            mongoTemplate.remove(Query(Criteria.where("_id").`is`(productId)), CatalogReadModel.VIEW)
            return
        }

        val variants = find(CatalogReadModel.VARIANTS, Criteria.where("product_id").`is`(productId))
            .sortedWith(
                compareByDescending<Document> { it.getBoolean("is_default") }
                    .thenBy { instant(it, "created_at") }
                    .thenBy { it.getString("_id") }
            )
        val variantIds = variants.map { it.getString("_id") }
        val images = find(CatalogReadModel.IMAGES, Criteria.where("variant_id").`in`(variantIds)).groupBy { it.getString("variant_id") }
        val tiers = find(CatalogReadModel.TIER_PRICING, Criteria.where("variant_id").`in`(variantIds)).groupBy { it.getString("variant_id") }

        val variantViews = variants.map { variantView(it, images[it.getString("_id")].orEmpty(), tiers[it.getString("_id")].orEmpty()) }

        val view = Document()
            .append("_id", productId)
            .append("slug", product.getString("slug"))
            .append("name", product.getString("name"))
            .append("description", product.getString("description"))
            .append("price", decimal(product, "price"))
            .append("status", product.getString("status"))
            .append("category", product.getString("category"))
            .append("tags", product.getString("tags"))
            .append("createdAt", date(product, "created_at"))
            .append("updatedAt", date(product, "updated_at"))
            .append("inStock", variants.isEmpty() || variants.any { it.getBoolean("in_stock") })
            .append("imageUrl", variantViews.firstOrNull()?.getList("images", Document::class.java)?.firstOrNull()?.getString("url"))
            .append("variants", variantViews)
        mongoTemplate.save(view, CatalogReadModel.VIEW)
    }

    private fun variantView(variant: Document, images: List<Document>, tiers: List<Document>) = Document()
        .append("id", variant.getString("_id"))
        .append("sku", variant.getString("sku"))
        .append("name", variant.getString("name"))
        .append("color", variant.getString("color"))
        .append("size", variant.getString("size"))
        .append("isDefault", variant.getBoolean("is_default"))
        .append("inStock", variant.getBoolean("in_stock"))
        .append("priceOverride", decimal(variant, "price_override"))
        .append("createdAt", date(variant, "created_at"))
        .append(
            "images",
            images.sortedWith(compareBy<Document> { it.getInteger("display_order") }.thenBy { it.getString("_id") })
                .map { Document("id", it.getString("_id")).append("url", it.getString("url")).append("displayOrder", it.getInteger("display_order")) }
        )
        .append(
            "tierPricing",
            tiers.sortedBy { it.getInteger("min_quantity") }
                .map { Document("id", it.getString("_id")).append("minQuantity", it.getInteger("min_quantity")).append("price", decimal(it, "price")) }
        )

    private fun find(collection: String, criteria: Criteria): List<Document> =
        mongoTemplate.find(Query(criteria), Document::class.java, collection)

    private fun instant(row: Document, field: String): Instant = Instant.parse(row.getString(field))

    private fun date(row: Document, field: String): Date = Date.from(instant(row, field))

    /** Debezium publishes NUMERIC columns as strings (`decimal.handling.mode: string`). */
    private fun decimal(row: Document, field: String): Decimal128? =
        row.getString(field)?.let { Decimal128(BigDecimal(it)) }
}
