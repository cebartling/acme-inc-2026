package com.acme.product.infrastructure.projection

import com.fasterxml.jackson.databind.ObjectMapper
import com.mongodb.client.MongoClients
import org.bson.Document
import org.bson.types.Decimal128
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mongodb.MongoDBContainer
import java.math.BigDecimal
import java.time.Instant
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Applies the row-change messages Debezium publishes for product's catalog tables (PIN-550) and
 * checks the `catalog_product_view` documents the projector builds from them.
 */
@Testcontainers
class CatalogChangeProjectorIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val mongo = MongoDBContainer("mongo:7.0")

        private const val PRODUCT_ID = "a10f4806-f226-4baa-b48d-189e8476ab65"
        private const val VARIANT_ID = "11111111-1111-1111-1111-000000000001"
        private const val OTHER_VARIANT_ID = "11111111-1111-1111-1111-000000000002"
    }

    private val objectMapper = ObjectMapper()

    private lateinit var mongoTemplate: MongoTemplate
    private lateinit var projector: CatalogChangeProjector

    @BeforeEach
    fun setUp() {
        mongoTemplate = MongoTemplate(MongoClients.create(mongo.connectionString), "catalog_projector_test")
        mongoTemplate.db.drop()
        val rebuilder = CatalogViewRebuilder(mongoTemplate)
        rebuilder.ensureIndexes()
        projector = CatalogChangeProjector(mongoTemplate, objectMapper, rebuilder)
    }

    // ---- messages, shaped like Debezium's ExtractNewRecordState output ----

    private fun message(row: Map<String, Any?>, op: String = "c", deleted: Boolean = false): String =
        objectMapper.writeValueAsString(
            row + mapOf("__op" to op, "__source_ts_ms" to 1794968919122L, "__deleted" to deleted.toString())
        )

    /**
     * A delete as Debezium sends it: the tables use the default replica identity, so only the id is
     * real and every other column is blank (a zero, "" or the epoch), not the row's old values.
     */
    private fun deleteMessage(table: String, id: String): String {
        val epoch = "1970-01-01T00:00:00.000000Z"
        val blank: Map<String, Any?> = when (table) {
            "products" -> mapOf(
                "slug" to "", "name" to "", "description" to null, "price" to "0.00", "status" to "",
                "category" to null, "tags" to null, "created_at" to epoch, "updated_at" to epoch
            )
            "product_variants" -> mapOf(
                "product_id" to "", "sku" to "", "name" to "", "color" to null, "size" to null,
                "is_default" to false, "in_stock" to true, "price_override" to null, "created_at" to epoch
            )
            "product_variant_images" -> mapOf("variant_id" to "", "url" to "", "display_order" to 0)
            "product_variant_tier_pricing" -> mapOf("variant_id" to "", "min_quantity" to 0, "price" to "0.00")
            else -> error("unknown table $table")
        }
        return message(blank + ("id" to id), op = "d", deleted = true)
    }

    private fun productRow(
        id: String = PRODUCT_ID,
        slug: String = "premium-widget",
        name: String = "Premium Widget",
        price: String = "49.99",
        status: String = "PUBLISHED",
        category: String? = "Electronics",
        createdAt: String = "2026-11-18T02:28:39.074402Z"
    ) = mapOf(
        "id" to id, "slug" to slug, "name" to name, "description" to "A widget", "price" to price,
        "status" to status, "category" to category, "tags" to "widget", "created_at" to createdAt,
        "updated_at" to createdAt
    )

    private fun variantRow(
        id: String = VARIANT_ID,
        productId: String = PRODUCT_ID,
        isDefault: Boolean = true,
        inStock: Boolean = true,
        priceOverride: String? = null,
        createdAt: String = "2026-11-18T02:28:39.284613Z"
    ) = mapOf(
        "id" to id, "product_id" to productId, "sku" to "SKU-$id", "name" to "Black", "color" to "Black",
        "size" to null, "is_default" to isDefault, "in_stock" to inStock, "price_override" to priceOverride,
        "created_at" to createdAt
    )

    private fun imageRow(id: String, variantId: String = VARIANT_ID, url: String, order: Int) =
        mapOf("id" to id, "variant_id" to variantId, "url" to url, "display_order" to order)

    private fun tierRow(id: String, variantId: String = VARIANT_ID, minQuantity: Int, price: String) =
        mapOf("id" to id, "variant_id" to variantId, "min_quantity" to minQuantity, "price" to price)

    private fun view(productId: String = PRODUCT_ID): Document? = mongoTemplate.findOne(
        Query(Criteria.where("_id").`is`(productId)), Document::class.java, CatalogReadModel.VIEW
    )

    @Suppress("UNCHECKED_CAST")
    private fun Document.variants() = get("variants") as List<Document>

    // ---- tests ----

    @Test
    fun `a product row creates its view document with typed fields`() {
        projector.apply("products", message(productRow()))

        val doc = assertNotNull(view())
        assertEquals("premium-widget", doc.getString("slug"))
        assertEquals("Premium Widget", doc.getString("name"))
        assertEquals("PUBLISHED", doc.getString("status"))
        assertEquals("Electronics", doc.getString("category"))
        assertEquals(BigDecimal("49.99"), doc.get("price", Decimal128::class.java).bigDecimalValue())
        assertEquals(Date.from(Instant.parse("2026-11-18T02:28:39.074Z")), doc.getDate("createdAt"))
        assertTrue(doc.variants().isEmpty())
    }

    @Test
    fun `a product without variants is in stock, with no image`() {
        projector.apply("products", message(productRow()))

        val doc = assertNotNull(view())
        assertEquals(true, doc.getBoolean("inStock"))
        assertNull(doc.getString("imageUrl"))
    }

    @Test
    fun `an update replaces the product's fields`() {
        projector.apply("products", message(productRow()))
        projector.apply("products", message(productRow(name = "Deluxe Widget", price = "59.99"), op = "u"))

        val doc = assertNotNull(view())
        assertEquals("Deluxe Widget", doc.getString("name"))
        assertEquals(BigDecimal("59.99"), doc.get("price", Decimal128::class.java).bigDecimalValue())
    }

    @Test
    fun `variants carry their images and tier pricing, default variant first, images by display order`() {
        projector.apply("products", message(productRow()))
        projector.apply(
            "product_variants",
            message(variantRow(OTHER_VARIANT_ID, isDefault = false, priceOverride = "39.99", createdAt = "2026-11-18T02:28:39.000000Z"))
        )
        projector.apply("product_variants", message(variantRow(VARIANT_ID, isDefault = true)))
        projector.apply("product_variant_images", message(imageRow("22222222-0000-0000-0000-000000000002", url = "/b.png", order = 2)))
        projector.apply("product_variant_images", message(imageRow("22222222-0000-0000-0000-000000000001", url = "/a.png", order = 1)))
        projector.apply("product_variant_tier_pricing", message(tierRow("33333333-0000-0000-0000-000000000002", minQuantity = 10, price = "40.00")))
        projector.apply("product_variant_tier_pricing", message(tierRow("33333333-0000-0000-0000-000000000001", minQuantity = 5, price = "45.00")))

        val doc = assertNotNull(view())
        val variants = doc.variants()
        assertEquals(listOf(VARIANT_ID, OTHER_VARIANT_ID), variants.map { it.getString("id") })
        assertEquals(
            BigDecimal("39.99"),
            variants[1].get("priceOverride", Decimal128::class.java).bigDecimalValue()
        )
        @Suppress("UNCHECKED_CAST")
        val images = variants[0].get("images") as List<Document>
        assertEquals(listOf("/a.png", "/b.png"), images.map { it.getString("url") })
        @Suppress("UNCHECKED_CAST")
        val tiers = variants[0].get("tierPricing") as List<Document>
        assertEquals(listOf(5, 10), tiers.map { it.getInteger("minQuantity") })
        // The card image is the first image of the default variant
        assertEquals("/a.png", doc.getString("imageUrl"))
    }

    @Test
    fun `a product is out of stock only when it has variants and none is in stock`() {
        projector.apply("products", message(productRow()))
        projector.apply("product_variants", message(variantRow(VARIANT_ID, inStock = true)))
        projector.apply("product_variants", message(variantRow(OTHER_VARIANT_ID, isDefault = false, inStock = false)))
        assertEquals(true, view()!!.getBoolean("inStock"))

        projector.apply("product_variants", message(variantRow(VARIANT_ID, inStock = false), op = "u"))

        assertEquals(false, view()!!.getBoolean("inStock"))
        assertEquals(false, view()!!.variants()[0].getBoolean("inStock"))
    }

    @Test
    fun `a variant that arrives before its product shows up once the product arrives`() {
        projector.apply("product_variants", message(variantRow()))
        assertNull(view())

        projector.apply("products", message(productRow()))

        assertEquals(listOf(VARIANT_ID), assertNotNull(view()).variants().map { it.getString("id") })
    }

    @Test
    fun `an image that arrives before its variant is included once the variant arrives`() {
        projector.apply("products", message(productRow()))
        projector.apply("product_variant_images", message(imageRow("22222222-0000-0000-0000-000000000001", url = "/a.png", order = 1)))
        assertTrue(view()!!.variants().isEmpty())

        projector.apply("product_variants", message(variantRow()))

        assertEquals("/a.png", view()!!.getString("imageUrl"))
    }

    @Test
    fun `applying the same messages again changes nothing`() {
        val messages = listOf(
            "products" to message(productRow()),
            "product_variants" to message(variantRow()),
            "product_variant_images" to message(imageRow("22222222-0000-0000-0000-000000000001", url = "/a.png", order = 1))
        )
        messages.forEach { (table, value) -> projector.apply(table, value) }
        val first = view()

        messages.forEach { (table, value) -> projector.apply(table, value) }

        assertEquals(first, view())
    }

    @Test
    fun `a deleted variant leaves the view, and the product's stock is recomputed`() {
        projector.apply("products", message(productRow()))
        projector.apply("product_variants", message(variantRow(VARIANT_ID, inStock = false)))
        projector.apply("product_variants", message(variantRow(OTHER_VARIANT_ID, isDefault = false, inStock = true)))
        assertEquals(true, view()!!.getBoolean("inStock"))

        projector.apply("product_variants", deleteMessage("product_variants", OTHER_VARIANT_ID))

        assertEquals(listOf(VARIANT_ID), view()!!.variants().map { it.getString("id") })
        assertEquals(false, view()!!.getBoolean("inStock"))
    }

    @Test
    fun `a deleted image leaves its variant, and the card image falls back to the next one`() {
        val first = "22222222-0000-0000-0000-000000000001"
        val second = "22222222-0000-0000-0000-000000000002"
        projector.apply("products", message(productRow()))
        projector.apply("product_variants", message(variantRow()))
        projector.apply("product_variant_images", message(imageRow(first, url = "/a.png", order = 0)))
        projector.apply("product_variant_images", message(imageRow(second, url = "/b.png", order = 1)))
        assertEquals("/a.png", view()!!.getString("imageUrl"))

        projector.apply("product_variant_images", deleteMessage("product_variant_images", first))

        assertEquals("/b.png", view()!!.getString("imageUrl"))
        assertEquals(listOf("/b.png"), view()!!.variants()[0].getList("images", Document::class.java).map { it.getString("url") })
    }

    @Test
    fun `a deleted tier price leaves its variant`() {
        val tier = "33333333-0000-0000-0000-000000000001"
        projector.apply("products", message(productRow()))
        projector.apply("product_variants", message(variantRow()))
        projector.apply("product_variant_tier_pricing", message(tierRow(tier, minQuantity = 5, price = "45.00")))
        assertEquals(1, view()!!.variants()[0].getList("tierPricing", Document::class.java).size)

        projector.apply("product_variant_tier_pricing", deleteMessage("product_variant_tier_pricing", tier))

        assertTrue(view()!!.variants()[0].getList("tierPricing", Document::class.java).isEmpty())
    }

    @Test
    fun `deleting a product and then its variants and images, as the cascade does, leaves nothing behind`() {
        val image = "22222222-0000-0000-0000-000000000001"
        projector.apply("products", message(productRow()))
        projector.apply("product_variants", message(variantRow()))
        projector.apply("product_variant_images", message(imageRow(image, url = "/a.png", order = 0)))

        projector.apply("products", deleteMessage("products", PRODUCT_ID))
        projector.apply("product_variants", deleteMessage("product_variants", VARIANT_ID))
        projector.apply("product_variant_images", deleteMessage("product_variant_images", image))

        assertNull(view())
        listOf(CatalogReadModel.PRODUCTS, CatalogReadModel.VARIANTS, CatalogReadModel.IMAGES).forEach {
            assertEquals(0L, mongoTemplate.getCollection(it).countDocuments(), "$it should be empty")
        }
    }

    @Test
    fun `a delete that is applied again changes nothing and does not fail`() {
        projector.apply("products", message(productRow()))
        projector.apply("product_variants", message(variantRow()))
        projector.apply("product_variants", deleteMessage("product_variants", VARIANT_ID))

        projector.apply("product_variants", deleteMessage("product_variants", VARIANT_ID))

        assertTrue(view()!!.variants().isEmpty())
    }

    @Test
    fun `a deleted product leaves the view`() {
        projector.apply("products", message(productRow()))
        projector.apply("product_variants", message(variantRow()))

        projector.apply("products", deleteMessage("products", PRODUCT_ID))

        assertNull(view())
    }

    @Test
    fun `a message that is not JSON fails instead of being skipped`() {
        assertFailsWith<Exception> { projector.apply("products", "not json") }
    }

    @Test
    fun `a row without an id fails instead of being skipped`() {
        assertFailsWith<IllegalArgumentException> {
            projector.apply("products", message(productRow() - "id"))
        }
    }

    @Test
    fun `a message for a table the projector does not know fails`() {
        assertFailsWith<IllegalArgumentException> { projector.apply("orders", message(productRow())) }
    }
}
