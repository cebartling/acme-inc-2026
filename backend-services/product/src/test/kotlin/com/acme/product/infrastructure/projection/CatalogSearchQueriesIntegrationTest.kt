package com.acme.product.infrastructure.projection

import com.acme.product.domain.SearchFilters
import com.acme.product.domain.SortOption
import com.mongodb.client.MongoClients
import org.bson.Document
import org.bson.types.Decimal128
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mongodb.MongoDBContainer
import java.math.BigDecimal
import java.time.Instant
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Product search and autocomplete (PIN-550) against a real MongoDB. They replace the PostgreSQL
 * full-text (`tsvector`) and `pg_trgm` queries; the use-case tests mock these, so they cannot catch
 * a wrong text query, ranking or filter.
 */
@Testcontainers
class CatalogSearchQueriesIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val mongo = MongoDBContainer("mongo:7.0")
    }

    private lateinit var mongoTemplate: MongoTemplate
    private lateinit var queries: CatalogSearchQueries

    private val base = Instant.parse("2026-01-01T00:00:00Z")

    @BeforeEach
    fun setUp() {
        mongoTemplate = MongoTemplate(MongoClients.create(mongo.connectionString), "catalog_search_test")
        mongoTemplate.db.drop()
        queries = CatalogSearchQueries(mongoTemplate)
        queries.ensureIndexes()
    }

    private fun product(
        name: String,
        description: String = "",
        category: String? = "Electronics",
        price: String = "10.00",
        status: String = "PUBLISHED",
        createdAt: Instant = base,
        inStock: Boolean = true,
        imageUrl: String? = null,
        id: UUID = UUID.randomUUID()
    ): UUID {
        mongoTemplate.save(
            Document()
                .append("_id", id.toString())
                .append("slug", name.lowercase().replace(' ', '-'))
                .append("name", name)
                .append("description", description)
                .append("price", Decimal128(BigDecimal(price)))
                .append("status", status)
                .append("category", category)
                .append("tags", null)
                .append("createdAt", Date.from(createdAt))
                .append("updatedAt", Date.from(createdAt))
                .append("inStock", inStock)
                .append("imageUrl", imageUrl)
                .append("variants", emptyList<Document>()),
            CatalogReadModel.VIEW
        )
        return id
    }

    private fun search(
        text: String,
        filters: SearchFilters = SearchFilters(),
        sort: SortOption = SortOption.RELEVANCE,
        offset: Int = 0,
        limit: Int = 24
    ) = queries.search(text, filters, sort, offset, limit).map { it.name }

    private fun seedCatalog() {
        product("Premium Widget", "A durable widget for everyday use", "Electronics", "49.99", createdAt = base)
        product("Widget Starter Kit", "Everything you need to start", "Electronics", "59.99", createdAt = base.plusSeconds(10))
        product("Gadget Pro", "A smart gadget with a premium finish", "Electronics", "119.99", createdAt = base.plusSeconds(20))
        product("Cotton Shirt", "Soft cotton shirts in every size", "Apparel", "19.99", createdAt = base.plusSeconds(30))
        product("Eco Widget", "Made from recycled material", "Home", "29.99", createdAt = base.plusSeconds(40))
        product("Archived Widget", "No longer sold", "Electronics", "9.99", status = "ARCHIVED", createdAt = base.plusSeconds(50))
    }

    // ---- matching ----

    @Test
    fun `a word matches the name, the description and the category, without regard to case`() {
        seedCatalog()

        assertEquals(setOf("Premium Widget", "Widget Starter Kit", "Eco Widget"), search("WIDGET").toSet())
        assertEquals(setOf("Gadget Pro", "Premium Widget"), search("premium").toSet())
        assertEquals(setOf("Cotton Shirt"), search("apparel").toSet())
    }

    @Test
    fun `a word matches its other forms, as PostgreSQL's english stemming did`() {
        seedCatalog()

        assertEquals(setOf("Premium Widget", "Widget Starter Kit", "Eco Widget"), search("widgets").toSet())
        assertEquals(setOf("Cotton Shirt"), search("shirt").toSet())
    }

    @Test
    fun `several words must all match, like plainto_tsquery`() {
        seedCatalog()

        assertEquals(listOf("Premium Widget"), search("premium widget"))
        assertEquals(listOf("Widget Starter Kit"), search("widget kit"))
        assertEquals(emptyList(), search("smart watch"))
    }

    @Test
    fun `an archived product is never found`() {
        seedCatalog()

        assertTrue("Archived Widget" !in search("widget"))
        assertEquals(3L, queries.count("widget", SearchFilters()))
    }

    @Test
    fun `no match is an empty result`() {
        seedCatalog()

        assertEquals(emptyList(), search("xyzzy-nonexistent-product-12345"))
        assertEquals(0L, queries.count("xyzzy", SearchFilters()))
    }

    @Test
    fun `punctuation and quotes in a query are not search syntax`() {
        seedCatalog()

        assertEquals(setOf("Premium Widget", "Widget Starter Kit", "Eco Widget"), search("\"widget\"").toSet())
        assertEquals(emptyList(), search("widget\" \"kit\" -premium"))
        assertEquals(emptyList(), search("   "))
        assertEquals(emptyList(), search("!!!"))
    }

    // ---- ordering and paging ----

    @Test
    fun `relevance puts a name match ahead of a description match`() {
        seedCatalog()

        assertEquals(listOf("Premium Widget", "Gadget Pro"), search("premium", sort = SortOption.RELEVANCE))
    }

    @Test
    fun `price and newest sorts order the matches, with the id as the tie-break`() {
        seedCatalog()

        assertEquals(listOf("Eco Widget", "Premium Widget", "Widget Starter Kit"), search("widget", sort = SortOption.PRICE_ASC))
        assertEquals(listOf("Widget Starter Kit", "Premium Widget", "Eco Widget"), search("widget", sort = SortOption.PRICE_DESC))
        assertEquals(listOf("Eco Widget", "Widget Starter Kit", "Premium Widget"), search("widget", sort = SortOption.NEWEST))
    }

    @Test
    fun `equal prices are ordered by id so pages never overlap`() {
        val first = product("Tie Widget A", price = "5.00", id = UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val second = product("Tie Widget B", price = "5.00", id = UUID.fromString("00000000-0000-0000-0000-000000000002"))

        assertEquals(
            listOf(first, second),
            queries.search("widget", SearchFilters(), SortOption.PRICE_ASC, 0, 10).map { it.id }
        )
    }

    @Test
    fun `a page is a slice of the sorted matches, and the count is the whole`() {
        seedCatalog()

        assertEquals(listOf("Eco Widget", "Premium Widget"), search("widget", sort = SortOption.PRICE_ASC, offset = 0, limit = 2))
        assertEquals(listOf("Widget Starter Kit"), search("widget", sort = SortOption.PRICE_ASC, offset = 2, limit = 2))
        assertEquals(3L, queries.count("widget", SearchFilters()))
    }

    @Test
    fun `a result carries its stock and image`() {
        product("Stocked Widget", inStock = true, imageUrl = "https://img/stocked")
        product("Sold Out Widget", inStock = false, imageUrl = null, createdAt = base.plusSeconds(1))

        val byName = queries.search("widget", SearchFilters(), SortOption.NEWEST, 0, 10).associateBy { it.name }

        assertEquals(true, byName.getValue("Stocked Widget").inStock)
        assertEquals("https://img/stocked", byName.getValue("Stocked Widget").imageUrl)
        assertEquals(false, byName.getValue("Sold Out Widget").inStock)
        assertNull(byName.getValue("Sold Out Widget").imageUrl)
        assertEquals(BigDecimal("10.00"), byName.getValue("Stocked Widget").price)
    }

    // ---- filters ----

    @Test
    fun `a category filter keeps the products of any of the categories`() {
        seedCatalog()

        assertEquals(setOf("Eco Widget"), search("widget", SearchFilters(categories = listOf("Home"))).toSet())
        assertEquals(
            setOf("Premium Widget", "Widget Starter Kit", "Eco Widget"),
            search("widget", SearchFilters(categories = listOf("Home", "Electronics"))).toSet()
        )
    }

    @Test
    fun `a price range is inclusive at both ends`() {
        seedCatalog()

        val range = SearchFilters(priceMin = BigDecimal("29.99"), priceMax = BigDecimal("49.99"))

        assertEquals(setOf("Eco Widget", "Premium Widget"), search("widget", range).toSet())
        assertEquals(2L, queries.count("widget", range))
    }

    @Test
    fun `category and price filters apply together, and the count follows them`() {
        seedCatalog()
        val filters = SearchFilters(categories = listOf("Electronics"), priceMin = BigDecimal("50.00"))

        assertEquals(listOf("Widget Starter Kit"), search("widget", filters))
        assertEquals(1L, queries.count("widget", filters))
    }

    // ---- facets ----

    @Test
    fun `facets count the matches per category, most first, ignoring any category filter`() {
        seedCatalog()

        assertEquals(
            listOf("Electronics" to 2L, "Home" to 1L),
            queries.categoryFacets("widget", priceMin = null, priceMax = null).toList()
        )
    }

    @Test
    fun `facets honour the price range`() {
        seedCatalog()

        assertEquals(
            listOf("Home" to 1L),
            queries.categoryFacets("widget", priceMin = BigDecimal("20.00"), priceMax = BigDecimal("30.00")).toList()
        )
    }

    @Test
    fun `products without a category are left out of the facets`() {
        product("Loose Widget", category = null)

        assertEquals(emptyMap(), queries.categoryFacets("widget", null, null))
    }

    // ---- spelling suggestion ----

    @Test
    fun `the suggestion is the most similar product name`() {
        seedCatalog()

        // pg_trgm similarity to "widgit": Eco Widget 0.286, Widget Starter Kit 0.238, Premium Widget 0.222
        assertEquals("Eco Widget", queries.spellingSuggestion("widgit"))
    }

    @Test
    fun `there is no suggestion when nothing is similar enough, and archived products are not suggested`() {
        seedCatalog()

        assertNull(queries.spellingSuggestion("qqqqq"))
        mongoTemplate.remove(Query(), CatalogReadModel.VIEW)
        product("Archived Widget", status = "ARCHIVED")
        assertNull(queries.spellingSuggestion("widgit"))
    }

    // ---- autocomplete ----

    @Test
    fun `product suggestions are the names that start with the prefix, most similar first`() {
        product("Widget Starter Kit")
        product("Widget")
        product("Premium Widget")
        product("Widget Pro Max Ultra Edition")

        val names = queries.autocompleteProducts("wid", limit = 10).map { it.name }

        // "Premium Widget" does not start with the prefix; the shorter names are more similar to it
        assertEquals(listOf("Widget", "Widget Starter Kit", "Widget Pro Max Ultra Edition"), names)
    }

    @Test
    fun `product suggestions are limited, skip archived products and carry the slug`() {
        product("Widget A")
        product("Widget B")
        product("Widget C")
        product("Widget Archived", status = "ARCHIVED")

        val suggestions = queries.autocompleteProducts("widget", limit = 2)

        assertEquals(2, suggestions.size)
        assertTrue(suggestions.all { it.slug.startsWith("widget-") && !it.name.contains("Archived") })
    }

    @Test
    fun `a prefix is taken literally, not as a pattern`() {
        product("Widget")
        product("100% Cotton")
        product("a_b")

        assertEquals(emptyList(), queries.autocompleteProducts(".*", 10))
        assertEquals(emptyList(), queries.autocompleteProducts("wid(", 10))
        assertEquals(listOf("100% Cotton"), queries.autocompleteProducts("100%", 10).map { it.name })
        assertEquals(listOf("a_b"), queries.autocompleteProducts("a_", 10).map { it.name })
        assertEquals(emptyList(), queries.autocompleteProducts("a%", 10))
    }

    @Test
    fun `category suggestions are the distinct categories that start with the prefix, in order`() {
        product("One", category = "Electronics")
        product("Two", category = "Electronics")
        product("Three", category = "Elegant Home")
        product("Four", category = "Apparel")
        product("Five", category = "Electric Bikes", status = "ARCHIVED")
        product("Six", category = null)

        assertEquals(listOf("Electronics", "Elegant Home"), queries.autocompleteCategories("ele", limit = 5))
        assertEquals(listOf("Electronics"), queries.autocompleteCategories("ELE", limit = 1))
        assertEquals(emptyList(), queries.autocompleteCategories("zzz", limit = 5))
    }
}
