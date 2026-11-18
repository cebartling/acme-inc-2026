package com.acme.product.infrastructure.projection

import com.mongodb.client.MongoClients
import org.bson.Document
import org.bson.types.Decimal128
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mongodb.MongoDBContainer
import java.math.BigDecimal
import java.time.Instant
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The catalog read endpoints' queries (PIN-550) against a real MongoDB. They ported the
 * PostgreSQL repository tests for category browsing and the variant lookup (US-0004-09, PIN-306),
 * which the controller and use-case tests, mocking the queries, cannot cover.
 *
 * The view documents are seeded directly; [CatalogChangeProjectorIntegrationTest] covers how the
 * projector builds them.
 */
@Testcontainers
class CatalogReadModelQueriesIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val mongo = MongoDBContainer("mongo:7.0")
    }

    private lateinit var mongoTemplate: MongoTemplate
    private lateinit var queries: CatalogReadModelQueries

    private val base = Instant.parse("2026-01-01T00:00:00Z")

    @BeforeEach
    fun setUp() {
        mongoTemplate = MongoTemplate(MongoClients.create(mongo.connectionString), "catalog_queries_test")
        mongoTemplate.db.drop()
        queries = CatalogReadModelQueries(mongoTemplate)
        queries.ensureIndexes()
    }

    private fun variantDoc(
        id: UUID = UUID.randomUUID(),
        isDefault: Boolean = true,
        inStock: Boolean = true,
        priceOverride: String? = null,
        images: List<String> = emptyList(),
        tiers: List<Pair<Int, String>> = emptyList()
    ) = Document()
        .append("id", id.toString())
        .append("sku", "SKU-$id")
        .append("name", "Default")
        .append("color", "Black")
        .append("size", null)
        .append("isDefault", isDefault)
        .append("inStock", inStock)
        .append("priceOverride", priceOverride?.let { Decimal128(BigDecimal(it)) })
        .append("createdAt", Date.from(base))
        .append("images", images.mapIndexed { i, url -> Document("id", UUID.randomUUID().toString()).append("url", url).append("displayOrder", i) })
        .append("tierPricing", tiers.map { (q, p) -> Document("id", UUID.randomUUID().toString()).append("minQuantity", q).append("price", Decimal128(BigDecimal(p))) })

    private fun product(
        slug: String,
        name: String = slug,
        category: String? = "Electronics",
        status: String = "PUBLISHED",
        price: String = "19.99",
        createdAt: Instant = base,
        inStock: Boolean = true,
        imageUrl: String? = null,
        variants: List<Document> = emptyList(),
        id: UUID = UUID.randomUUID()
    ): UUID {
        mongoTemplate.save(
            Document()
                .append("_id", id.toString())
                .append("slug", slug)
                .append("name", name)
                .append("description", "About $name")
                .append("price", Decimal128(BigDecimal(price)))
                .append("status", status)
                .append("category", category)
                .append("tags", "a,b")
                .append("createdAt", Date.from(createdAt))
                .append("updatedAt", Date.from(createdAt))
                .append("inStock", inStock)
                .append("imageUrl", imageUrl)
                .append("variants", variants),
            CatalogReadModel.VIEW
        )
        return id
    }

    private fun seedCategories() {
        // Newest first by createdAt, so browse order is electronics-3, -2, -1.
        product("electronics-1", category = "Electronics", createdAt = base)
        product("electronics-2", category = "Electronics", createdAt = base.plusSeconds(60))
        product("electronics-3", category = "Electronics", createdAt = base.plusSeconds(120))
        product("apparel-1", category = "Apparel", createdAt = base)
        // Must be excluded: archived, and null category.
        product("electronics-archived", category = "Electronics", status = "ARCHIVED")
        product("uncategorized", category = null)
    }

    // ---- categories (US-0004-09) ----

    @Test
    fun `listCategories returns categories alphabetically with published counts`() {
        seedCategories()

        val categories = queries.listCategories()

        assertEquals(listOf("Apparel", "Electronics"), categories.map { it.name })
        assertEquals(1L, categories[0].productCount)
        assertEquals(3L, categories[1].productCount, "archived products must not be counted")
    }

    @Test
    fun `listCategories sorts without regard to case`() {
        product("a", category = "apparel")
        product("b", category = "Books")

        assertEquals(listOf("apparel", "Books"), queries.listCategories().map { it.name })
    }

    @Test
    fun `listCategories leaves out a category whose only products are archived`() {
        seedCategories()
        product("tools-archived", category = "Tools", status = "ARCHIVED")

        assertEquals(listOf("Apparel", "Electronics"), queries.listCategories().map { it.name })
    }

    @Test
    fun `findByCategory returns only published products in that category`() {
        seedCategories()

        val products = queries.findByCategory("Electronics", page = 1, pageSize = 100)

        assertEquals(3, products.size)
        assertTrue(products.all { it.category == "Electronics" })
    }

    @Test
    fun `findByCategory orders newest first and pages without overlap`() {
        seedCategories()

        val first = queries.findByCategory("Electronics", page = 1, pageSize = 2)
        val second = queries.findByCategory("Electronics", page = 2, pageSize = 2)

        assertEquals(listOf("electronics-3", "electronics-2"), first.map { it.slug })
        assertEquals(listOf("electronics-1"), second.map { it.slug })
    }

    @Test
    fun `a product summary carries the stored stock and image`() {
        product("out", inStock = false, imageUrl = null)
        product("in", inStock = true, imageUrl = "https://img/in", createdAt = base.plusSeconds(1))

        val products = queries.findByCategory("Electronics", page = 1, pageSize = 24)

        assertEquals(listOf("in", "out"), products.map { it.slug })
        assertEquals(listOf(true, false), products.map { it.inStock })
        assertEquals(listOf("https://img/in", null), products.map { it.imageUrl })
        assertEquals(BigDecimal("19.99"), products[0].price)
    }

    @Test
    fun `countByCategory matches the listed count, and every listed category browses to that many`() {
        seedCategories()

        queries.listCategories().forEach { category ->
            assertEquals(category.productCount, queries.countByCategory(category.name))
            assertEquals(
                category.productCount,
                queries.findByCategory(category.name, page = 1, pageSize = 100).size.toLong()
            )
        }
    }

    @Test
    fun `an unknown category has no products`() {
        seedCategories()

        assertTrue(queries.findByCategory("NoSuchCategory", page = 1, pageSize = 24).isEmpty())
        assertEquals(0L, queries.countByCategory("NoSuchCategory"))
    }

    // ---- product detail ----

    @Test
    fun `findBySlug returns a published product with its variants, images and tier pricing`() {
        val variantId = UUID.randomUUID()
        val id = product(
            "premium-widget",
            price = "49.99",
            inStock = true,
            imageUrl = "/a.png",
            variants = listOf(
                variantDoc(variantId, priceOverride = "39.99", images = listOf("/a.png", "/b.png"), tiers = listOf(5 to "35.00"))
            )
        )

        val found = assertNotNull(queries.findBySlug("premium-widget"))

        assertEquals(id, found.id)
        assertEquals(BigDecimal("49.99"), found.price)
        assertEquals("a,b", found.tags)
        assertEquals("/a.png", found.imageUrl)
        val variant = found.variants.single()
        assertEquals(variantId, variant.id)
        assertEquals(BigDecimal("39.99"), variant.priceOverride)
        assertEquals(listOf("/a.png", "/b.png"), variant.images)
        assertEquals(listOf(5), variant.tierPricing.map { it.minQuantity })
        assertEquals(BigDecimal("35.00"), variant.tierPricing.single().price)
    }

    @Test
    fun `findBySlug does not find an archived product or an unknown slug`() {
        product("archived", status = "ARCHIVED")

        assertNull(queries.findBySlug("archived"))
        assertNull(queries.findBySlug("nope"))
    }

    @Test
    fun `findInStockRelated returns in-stock published products of the category, newest first, without the excluded one`() {
        val self = product("self", createdAt = base)
        product("older-in", createdAt = base.plusSeconds(10))
        product("newest-in", createdAt = base.plusSeconds(30))
        product("out", inStock = false, createdAt = base.plusSeconds(20))
        product("archived", status = "ARCHIVED", createdAt = base.plusSeconds(40))
        product("other-category", category = "Apparel", createdAt = base.plusSeconds(50))

        val related = queries.findInStockRelated("Electronics", excludeId = self, limit = 4)

        assertEquals(listOf("newest-in", "older-in"), related.map { it.slug })
    }

    @Test
    fun `findInStockRelated stops at the limit, finding older in-stock products behind newer out-of-stock ones`() {
        val self = product("self")
        repeat(5) { product("out-$it", inStock = false, createdAt = base.plusSeconds(100L + it)) }
        repeat(3) { product("in-$it", createdAt = base.plusSeconds(10L + it)) }

        assertEquals(2, queries.findInStockRelated("Electronics", excludeId = self, limit = 2).size)
        assertEquals(3, queries.findInStockRelated("Electronics", excludeId = self, limit = 4).size)
    }

    // ---- variant lookup (PIN-306) ----

    @Test
    fun `a published product's variant is found with the product's price`() {
        val variantId = UUID.randomUUID()
        product("p", price = "19.99", variants = listOf(variantDoc(variantId, inStock = false, priceOverride = "9.99")))

        val found = assertNotNull(queries.findVariant(variantId))

        assertEquals(variantId, found.variant.id)
        assertEquals(false, found.variant.inStock)
        assertEquals(BigDecimal("9.99"), found.variant.priceOverride)
        assertEquals(BigDecimal("19.99"), found.productPrice)
    }

    @Test
    fun `an archived product's variant is not found`() {
        val variantId = UUID.randomUUID()
        product("archived", status = "ARCHIVED", variants = listOf(variantDoc(variantId)))

        assertNull(queries.findVariant(variantId))
    }

    @Test
    fun `an unknown variant is not found, and a variant is found only in its own product`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        product("one", price = "10.00", variants = listOf(variantDoc(first)))
        product("two", price = "20.00", variants = listOf(variantDoc(second)))

        assertNull(queries.findVariant(UUID.randomUUID()))
        assertEquals(BigDecimal("20.00"), queries.findVariant(second)?.productPrice)
    }
}
