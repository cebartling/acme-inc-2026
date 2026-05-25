package com.acme.product.application

import com.acme.product.domain.SearchQuery
import com.acme.product.domain.SortOption
import com.acme.product.domain.events.SearchExecuted
import com.acme.product.infrastructure.messaging.ProductEventPublisher
import com.acme.product.infrastructure.persistence.ProductRepository
import com.acme.product.infrastructure.persistence.ProductSearchProjection
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

    private lateinit var repository: ProductRepository
    private lateinit var eventPublisher: ProductEventPublisher
    private lateinit var useCase: SearchProductsUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        eventPublisher = mockk()
        useCase = SearchProductsUseCase(repository, eventPublisher)
    }

    private fun createProjection(
        id: UUID = UUID.randomUUID(),
        slug: String = "test-product",
        name: String = "Test Product",
        price: BigDecimal = BigDecimal("29.99"),
        category: String? = "Electronics"
    ): ProductSearchProjection {
        val projection = mockk<ProductSearchProjection>()
        every { projection.getId() } returns id
        every { projection.getSlug() } returns slug
        every { projection.getName() } returns name
        every { projection.getPrice() } returns price
        every { projection.getCategory() } returns category
        return projection
    }

    @Test
    fun `execute should return correct SearchResult shape with matching products`() {
        // Given
        val query = SearchQuery(query = "widget", page = 1, pageSize = 24, sort = SortOption.RELEVANCE)
        val projection1 = createProjection(slug = "premium-widget", name = "Premium Widget", price = BigDecimal("49.99"))
        val projection2 = createProjection(slug = "basic-widget", name = "Basic Widget", price = BigDecimal("9.99"))

        every { repository.searchByRelevance("widget", 24, 0) } returns listOf(projection1, projection2)
        every { repository.countByQuery("widget") } returns 2L
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
    fun `execute should publish SearchExecuted event`() {
        // Given
        val query = SearchQuery(query = "gadget", page = 1, pageSize = 24, sort = SortOption.RELEVANCE)
        val projection = createProjection(name = "Super Gadget")
        val eventSlot = slot<SearchExecuted>()

        every { repository.searchByRelevance("gadget", 24, 0) } returns listOf(projection)
        every { repository.countByQuery("gadget") } returns 1L
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

        every { repository.searchByRelevance("wiget", 24, 0) } returns emptyList()
        every { repository.countByQuery("wiget") } returns 0L
        every { repository.findSpellingSuggestion("wiget") } returns "Premium Widget"
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

        every { repository.searchByRelevance("widget", 24, 0) } returns listOf(projection)
        every { repository.countByQuery("widget") } returns 1L
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

        every { repository.searchByPriceAsc("widget", 10, 0) } returns emptyList()
        every { repository.countByQuery("widget") } returns 0L
        every { repository.findSpellingSuggestion("widget") } returns null
        every { eventPublisher.publish(any()) } just Runs

        // When
        useCase.execute(query)

        // Then
        verify(exactly = 1) { repository.searchByPriceAsc("widget", 10, 0) }
        verify(exactly = 0) { repository.searchByRelevance(any(), any(), any()) }
    }

    @Test
    fun `execute should calculate totalPages correctly`() {
        // Given
        val query = SearchQuery(query = "product", page = 1, pageSize = 10, sort = SortOption.RELEVANCE)
        val projections = (1..10).map { createProjection(name = "Product $it") }

        every { repository.searchByRelevance("product", 10, 0) } returns projections
        every { repository.countByQuery("product") } returns 25L
        every { eventPublisher.publish(any()) } just Runs

        // When
        val result = useCase.execute(query)

        // Then
        assertEquals(3, result.totalPages)
        assertEquals(25L, result.totalResults)
    }
}
