package com.acme.product.infrastructure.projection

import com.acme.product.domain.CatalogProduct
import com.acme.product.domain.CatalogVariant
import com.acme.product.domain.CategoryCount
import com.acme.product.domain.ProductSummary
import com.acme.product.domain.TierPrice
import com.acme.product.domain.VariantOfProduct
import jakarta.annotation.PostConstruct
import org.bson.Document
import org.bson.types.Decimal128
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.UUID

/**
 * Reads the catalog read model (PIN-550): the `catalog_product_view` documents the projector
 * builds. Every query returns published products only, like the PostgreSQL queries it replaced.
 * The reads are eventually consistent: a catalog change shows once the projector has applied it.
 */
@Component
class CatalogReadModelQueries(
    private val mongoTemplate: MongoTemplate
) {

    @PostConstruct
    fun ensureIndexes() {
        val indexes = mongoTemplate.indexOps(CatalogReadModel.VIEW)
        indexes.ensureIndex(Index().on("slug", Sort.Direction.ASC))
        indexes.ensureIndex(Index().on("category", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC))
        indexes.ensureIndex(Index().on("variants.id", Sort.Direction.ASC))
    }

    /** Categories with at least one published product, with their counts, alphabetically without regard to case. */
    fun listCategories(): List<CategoryCount> =
        mongoTemplate.aggregate(
            Aggregation.newAggregation(
                Aggregation.match(published().and("category").ne(null)),
                Aggregation.group("category").count().`as`("count")
            ),
            CatalogReadModel.VIEW,
            Document::class.java
        ).mappedResults
            .map { CategoryCount(it.getString("_id"), it.getInteger("count").toLong()) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    fun countByCategory(category: String): Long =
        mongoTemplate.count(Query(published().and("category").`is`(category)), CatalogReadModel.VIEW)

    /** A page of the category's products, newest first. [page] is 1-based. */
    fun findByCategory(category: String, page: Int, pageSize: Int): List<ProductSummary> =
        mongoTemplate.find(
            Query(published().and("category").`is`(category))
                .with(NEWEST_FIRST)
                .skip((page - 1).toLong() * pageSize)
                .limit(pageSize),
            Document::class.java,
            CatalogReadModel.VIEW
        ).map { toSummary(it) }

    fun findBySlug(slug: String): CatalogProduct? =
        find(published().and("slug").`is`(slug)).firstOrNull()?.let { toProduct(it) }

    /** In-stock products in [category] other than [excludeId], newest first. */
    fun findInStockRelated(category: String, excludeId: UUID, limit: Int): List<ProductSummary> =
        mongoTemplate.find(
            Query(
                published().and("category").`is`(category)
                    .and("inStock").`is`(true)
                    .and("_id").ne(excludeId.toString())
            ).with(NEWEST_FIRST).limit(limit),
            Document::class.java,
            CatalogReadModel.VIEW
        ).map { toSummary(it) }

    /** The variant with the price of the published product it belongs to; null if there is none. */
    fun findVariant(variantId: UUID): VariantOfProduct? {
        val product = find(published().and("variants.id").`is`(variantId.toString())).firstOrNull() ?: return null
        val variant = variants(product).first { it.getString("id") == variantId.toString() }
        return VariantOfProduct(variant = toVariant(variant), productPrice = decimal(product, "price")!!)
    }

    private fun published() = Criteria.where("status").`is`("PUBLISHED")

    private fun find(criteria: Criteria): List<Document> =
        mongoTemplate.find(Query(criteria), Document::class.java, CatalogReadModel.VIEW)

    @Suppress("UNCHECKED_CAST")
    private fun variants(product: Document): List<Document> = product.get("variants") as List<Document>

    private fun toSummary(doc: Document) = ProductSummary(
        id = UUID.fromString(doc.getString("_id")),
        slug = doc.getString("slug"),
        name = doc.getString("name"),
        price = decimal(doc, "price")!!,
        category = doc.getString("category"),
        inStock = doc.getBoolean("inStock"),
        imageUrl = doc.getString("imageUrl")
    )

    private fun toProduct(doc: Document) = CatalogProduct(
        id = UUID.fromString(doc.getString("_id")),
        slug = doc.getString("slug"),
        name = doc.getString("name"),
        description = doc.getString("description"),
        price = decimal(doc, "price")!!,
        category = doc.getString("category"),
        tags = doc.getString("tags"),
        inStock = doc.getBoolean("inStock"),
        imageUrl = doc.getString("imageUrl"),
        variants = variants(doc).map { toVariant(it) }
    )

    private fun toVariant(doc: Document) = CatalogVariant(
        id = UUID.fromString(doc.getString("id")),
        sku = doc.getString("sku"),
        name = doc.getString("name"),
        color = doc.getString("color"),
        size = doc.getString("size"),
        isDefault = doc.getBoolean("isDefault"),
        inStock = doc.getBoolean("inStock"),
        priceOverride = decimal(doc, "priceOverride"),
        images = doc.getList("images", Document::class.java).map { it.getString("url") },
        tierPricing = doc.getList("tierPricing", Document::class.java)
            .map { TierPrice(it.getInteger("minQuantity"), decimal(it, "price")!!) }
    )

    private fun decimal(doc: Document, field: String): BigDecimal? =
        doc.get(field, Decimal128::class.java)?.bigDecimalValue()

    private companion object {
        val NEWEST_FIRST: Sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("_id"))
    }
}
