package com.acme.product.application

import com.acme.product.domain.SearchFilters
import com.acme.product.domain.SearchQuery
import com.acme.product.domain.SortOption
import com.acme.product.domain.events.FiltersApplied
import com.acme.product.domain.events.SearchExecuted
import com.acme.product.infrastructure.messaging.ProductEventPublisher
import com.acme.product.domain.ProductSummary
import com.acme.product.infrastructure.projection.CatalogSearchQueries
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchProductsUseCaseTest {

    private lateinit var queries: CatalogSearchQueries
    private lateinit var eventPublisher: ProductEventPublisher
    private lateinit var useCase: SearchProductsUseCase

    @BeforeEach
    fun setUp() {
        queries = mockk()
        eventPublisher = mockk()
        useCase = SearchProductsUseCase(queries, eventPublisher)
    }

    private fun createProjection(
        id: UUID = UUID.randomUUID(),
        slug: String = "test-product",
        name: String = "Test Product",
        price: BigDecimal = BigDecimal("29.99"),
        category: String? = "Electronics",
        inStock: Boolean = true,
        imageUrl: String? = null
    ) = ProductSummary(id, slug, name, price, category, inStock, imageUrl)

    @Test
    fun `execute should return correct SearchResult shape with matching products`() {
        // Given
        val query = SearchQuery(query = "widget", page = 1, pageSize = 24, sort = SortOption.RELEVANCE)
        val projection1 = createProjection(slug = "premium-widget", name = "Premium Widget", price = BigDecimal("49.99"))
        val projection2 = createProjection(slug = "basic-widget", name = "Basic Widget", price = BigDecimal("9.99"))

        every { queries.search("widget", SearchFilters(), SortOption.RELEVANCE, 0, 24) } returns listOf(projection1, projection2)
        every { queries.count("widget", SearchFilters()) } returns 2L
        every { queries.categoryFacets("widget", null, null) } returns emptyMap()
        every { eventPublisher.publish(any()) } just Runs

        // When
        val result = useCase.execute(query)

        // Then
        assertEquals(2, result.products.size)
        assertEquals(2L, result.totalResults)
        assertEquals(1, result.page)
        assertEquals(24, result.pageSize)
        assertEquals(1, result.totalPages)
        assertNull(result.spellingSuggestion)
        assertTrue(result.executionTimeMs >= 0)

        assertEquals("Premium Widget", result.products[0].name)
        assertEquals("premium-widget", result.products[0].slug)
        assertEquals(BigDecimal("49.99"), result.products[0].price)

        assertEquals("Basic Widget", result.products[1].name)
    }

    @Test
    fun `execute should return category facets in result`() {
        // Given
        val query = SearchQuery(query = "widget", page = 1, pageSize = 24, sort = SortOption.RELEVANCE)
        val projection = createProjection()

        every { queries.search("widget", SearchFilters(), SortOption.RELEVANCE, 0, 24) } returns listOf(projection)
        every { queries.count("widget", SearchFilters()) } returns 1L
        every { queries.categoryFacets("widget", null, null) } returns mapOf("Electronics" to 5L, "Gaming" to 3L)
        every { eventPublisher.publish(any()) } just Runs

        // When
        val result = useCase.execute(query)

        // Then
        assertEquals(mapOf("Electronics" to 5L, "Gaming" to 3L), result.facets.categories)
    }

    @Test
    fun `execute should publish SearchExecuted event`() {
        // Given
        val query = SearchQuery(query = "gadget", page = 1, pageSize = 24, sort = SortOption.RELEVANCE)
        val projection = createProjection(name = "Super Gadget")
        val eventSlot = slot<SearchExecuted>()

        every { queries.search("gadget", SearchFilters(), SortOption.RELEVANCE, 0, 24) } returns listOf(projection)
        every { queries.count("gadget", SearchFilters()) } returns 1L
        every { queries.categoryFacets("gadget", null, null) } returns emptyMap()
        every { eventPublisher.publish(capture(eventSlot)) } just Runs

        // When
        useCase.execute(query, sessionId = "session-123")

        // Then
        verify(exactly = 1) { eventPublisher.publish(any()) }
        val event = eventSlot.captured
        assertEquals("gadget", event.payload.query)
        assertEquals(1L, event.payload.totalResults)
        assertEquals(1, event.payload.page)
        assertEquals("session-123", event.payload.sessionId)
    }

    @Test
    fun `execute should return empty list and spelling suggestion when zero results`() {
        // Given
        val query = SearchQuery(query = "wiget", page = 1, pageSize = 24, sort = SortOption.RELEVANCE)

        every { queries.search("wiget", SearchFilters(), SortOption.RELEVANCE, 0, 24) } returns emptyList()
        every { queries.count("wiget", SearchFilters()) } returns 0L
        every { queries.spellingSuggestion("wiget") } returns "Premium Widget"
        every { queries.categoryFacets("wiget", null, null) } returns emptyMap()
        every { eventPublisher.publish(any()) } just Runs

        // When
        val result = useCase.execute(query)

        // Then
        assertTrue(result.products.isEmpty())
        assertEquals(0L, result.totalResults)
        assertEquals(0, result.totalPages)
        assertNotNull(result.spellingSuggestion)
        assertEquals("Premium Widget", result.spellingSuggestion)
    }

    @Test
    fun `execute should not fail when event publishing throws exception`() {
        // Given
        val query = SearchQuery(query = "widget", page = 1, pageSize = 24, sort = SortOption.RELEVANCE)
        val projection = createProjection(name = "Premium Widget")

        every { queries.search("widget", SearchFilters(), SortOption.RELEVANCE, 0, 24) } returns listOf(projection)
        every { queries.count("widget", SearchFilters()) } returns 1L
        every { queries.categoryFacets("widget", null, null) } returns emptyMap()
        every { eventPublisher.publish(any()) } throws RuntimeException("Kafka unavailable")

        // When
        val result = useCase.execute(query)

        // Then
        assertEquals(1, result.products.size)
        assertEquals(1L, result.totalResults)
    }

    @Test
    fun `execute should delegate to correct sort method`() {
        // Given
        val query = SearchQuery(query = "widget", page = 1, pageSize = 10, sort = SortOption.PRICE_ASC)

        every { queries.search("widget", SearchFilters(), SortOption.PRICE_ASC, 0, 10) } returns emptyList()
        every { queries.count("widget", SearchFilters()) } returns 0L
        every { queries.spellingSuggestion("widget") } returns null
        every { queries.categoryFacets("widget", null, null) } returns emptyMap()
        every { eventPublisher.publish(any()) } just Runs

        // When
        useCase.execute(query)

        // Then
        verify(exactly = 1) { queries.search("widget", SearchFilters(), SortOption.PRICE_ASC, 0, 10) }
        verify(exactly = 0) { queries.search(any(), any(), SortOption.RELEVANCE, any(), any()) }
    }

    @Test
    fun `execute should calculate totalPages correctly`() {
        // Given
        val query = SearchQuery(query = "product", page = 1, pageSize = 10, sort = SortOption.RELEVANCE)
        val projections = (1..10).map { createProjection(name = "Product $it") }

        every { queries.search("product", SearchFilters(), SortOption.RELEVANCE, 0, 10) } returns projections
        every { queries.count("product", SearchFilters()) } returns 25L
        every { queries.categoryFacets("product", null, null) } returns emptyMap()
        every { eventPublisher.publish(any()) } just Runs

        // When
        val result = useCase.execute(query)

        // Then
        assertEquals(3, result.totalPages)
        assertEquals(25L, result.totalResults)
    }

    @Test
    fun `execute with category filter should pass the filters to the search and the count`() {
        // Given
        val filters = SearchFilters(categories = listOf("Electronics", "Gaming"))
        val query = SearchQuery(query = "widget", page = 1, pageSize = 24, sort = SortOption.RELEVANCE, filters = filters)
        val projection = createProjection(category = "Electronics")

        every { queries.search("widget", filters, SortOption.RELEVANCE, 0, 24) } returns listOf(projection)
        every { queries.count("widget", filters) } returns 1L
        every { queries.categoryFacets("widget", null, null) } returns mapOf("Electronics" to 1L)
        every { eventPublisher.publish(any()) } just Runs

        // When
        val result = useCase.execute(query)

        // Then
        assertEquals(1, result.products.size)
        assertEquals(1L, result.totalResults)
        verify(exactly = 1) { queries.search("widget", filters, SortOption.RELEVANCE, 0, 24) }
    }

    @Test
    fun `execute with price filter should pass the filters to the search and the count, and the price range to the facets`() {
        // Given
        val filters = SearchFilters(priceMin = BigDecimal("25.00"), priceMax = BigDecimal("75.00"))
        val query = SearchQuery(query = "mouse", page = 1, pageSize = 24, sort = SortOption.RELEVANCE, filters = filters)
        val projection = createProjection(price = BigDecimal("49.99"))

        every { queries.search("mouse", filters, SortOption.RELEVANCE, 0, 24) } returns listOf(projection)
        every { queries.count("mouse", filters) } returns 1L
        every { queries.categoryFacets("mouse", BigDecimal("25.00"), BigDecimal("75.00")) } returns emptyMap()
        every { eventPublisher.publish(any()) } just Runs

        // When
        val result = useCase.execute(query)

        // Then
        assertEquals(1, result.products.size)
        verify(exactly = 1) { queries.search("mouse", filters, SortOption.RELEVANCE, 0, 24) }
    }

    @Test
    fun `execute with active filters should publish FiltersApplied event`() {
        // Given
        val filters = SearchFilters(categories = listOf("Gaming"))
        val query = SearchQuery(query = "headset", page = 1, pageSize = 24, sort = SortOption.RELEVANCE, filters = filters)
        val projection = createProjection(category = "Gaming")
        val publishedEvents = mutableListOf<com.acme.product.domain.events.DomainEvent>()

        every { queries.search("headset", filters, SortOption.RELEVANCE, 0, 24) } returns listOf(projection)
        every { queries.count("headset", filters) } returns 1L
        every { queries.categoryFacets("headset", null, null) } returns emptyMap()
        every { eventPublisher.publish(capture(publishedEvents)) } just Runs

        // When
        useCase.execute(query, sessionId = "sess_abc")

        // Then
        assertEquals(2, publishedEvents.size)
        val searchEvent = publishedEvents.filterIsInstance<SearchExecuted>().single()
        val filtersEvent = publishedEvents.filterIsInstance<FiltersApplied>().single()

        assertEquals("headset", searchEvent.payload.query)
        assertEquals("headset", filtersEvent.payload.query)
        assertEquals(listOf("Gaming"), filtersEvent.payload.categories)
        assertEquals(1L, filtersEvent.payload.resultCount)
        assertEquals("sess_abc", filtersEvent.payload.sessionId)
    }

    @Test
    fun `execute without filters should not publish FiltersApplied event`() {
        // Given
        val query = SearchQuery(query = "mouse", page = 1, pageSize = 24, sort = SortOption.RELEVANCE)
        val projection = createProjection()
        val publishedEvents = mutableListOf<com.acme.product.domain.events.DomainEvent>()

        every { queries.search("mouse", SearchFilters(), SortOption.RELEVANCE, 0, 24) } returns listOf(projection)
        every { queries.count("mouse", SearchFilters()) } returns 1L
        every { queries.categoryFacets("mouse", null, null) } returns emptyMap()
        every { eventPublisher.publish(capture(publishedEvents)) } just Runs

        // When
        useCase.execute(query)

        // Then
        assertEquals(1, publishedEvents.size)
        assertTrue(publishedEvents.single() is SearchExecuted)
    }

    // US-0004-10 AC-06 (PIN-273): search cards show an Out of Stock badge and an image, which the
    // read model holds on each product
    @Test
    fun `execute should give each result its stock and image`() {
        val query = SearchQuery(query = "widget", page = 1, pageSize = 24, sort = SortOption.RELEVANCE)
        every { queries.search("widget", SearchFilters(), SortOption.RELEVANCE, 0, 24) } returns listOf(
            createProjection(inStock = false),
            createProjection(inStock = true, imageUrl = "https://img/in"),
            createProjection()
        )
        every { queries.count("widget", SearchFilters()) } returns 3L
        every { queries.categoryFacets("widget", null, null) } returns emptyMap()
        every { eventPublisher.publish(any()) } just Runs

        val products = useCase.execute(query).products

        assertEquals(listOf(false, true, true), products.map { it.inStock })
        assertEquals(listOf(null, "https://img/in", null), products.map { it.imageUrl })
    }

    // Facets are best-effort: a failure must not 500 search, which would also count toward the
    // customer app's search circuit breaker
    @Test
    fun `execute should still answer, with no facets, when the facets query fails`() {
        val query = SearchQuery(query = "widget", page = 1, pageSize = 24, sort = SortOption.RELEVANCE)
        every { queries.search("widget", SearchFilters(), SortOption.RELEVANCE, 0, 24) } returns listOf(createProjection())
        every { queries.count("widget", SearchFilters()) } returns 1L
        every { queries.categoryFacets("widget", null, null) } throws RuntimeException("connection reset")
        every { eventPublisher.publish(any()) } just Runs

        val result = useCase.execute(query)

        assertEquals(1, result.products.size)
        assertEquals(emptyMap(), result.facets.categories)
    }
}
