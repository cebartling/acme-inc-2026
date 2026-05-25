package com.acme.product.api.v1

import com.acme.product.application.SearchProductsUseCase
import com.acme.product.domain.ProductSummary
import com.acme.product.domain.SearchQuery
import com.acme.product.domain.SearchResult
import com.acme.product.domain.SortOption
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SearchControllerTest {

    private lateinit var searchProductsUseCase: SearchProductsUseCase
    private lateinit var controller: SearchController

    @BeforeEach
    fun setUp() {
        searchProductsUseCase = mockk()
        controller = SearchController(searchProductsUseCase)
    }

    @Test
    fun `search should return 200 with correct response shape`() {
        // Given
        val productId = UUID.randomUUID()
        val searchResult = SearchResult(
            products = listOf(
                ProductSummary(
                    id = productId,
                    slug = "premium-widget",
                    name = "Premium Widget",
                    price = BigDecimal("49.99"),
                    category = "Electronics"
                )
            ),
            totalResults = 1L,
            page = 1,
            pageSize = 24,
            totalPages = 1,
            spellingSuggestion = null,
            executionTimeMs = 15
        )

        every { searchProductsUseCase.execute(any(), any(), any()) } returns searchResult

        val request = SearchRequest(query = "widget", page = 1, pageSize = 24, sort = "relevance")

        // When
        val response = controller.search(request, correlationId = null, sessionId = null)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body
        assertNotNull(body)
        assertEquals("widget", body.query)
        assertEquals(1L, body.totalResults)
        assertEquals(1, body.page)
        assertEquals(24, body.pageSize)
        assertEquals(1, body.totalPages)
        assertEquals(1, body.results.size)
        assertNull(body.spellingSuggestion)
        assertEquals(15, body.executionTimeMs)

        val product = body.results[0]
        assertEquals(productId, product.id)
        assertEquals("premium-widget", product.slug)
        assertEquals("Premium Widget", product.name)
        assertEquals(BigDecimal("49.99"), product.price)
        assertEquals("Electronics", product.category)
    }

    @Test
    fun `search should pass correct SearchQuery to use case`() {
        // Given
        val querySlot = slot<SearchQuery>()
        val searchResult = SearchResult(
            products = emptyList(),
            totalResults = 0L,
            page = 2,
            pageSize = 10,
            totalPages = 0,
            spellingSuggestion = null,
            executionTimeMs = 5
        )

        every { searchProductsUseCase.execute(capture(querySlot), any(), any()) } returns searchResult

        val request = SearchRequest(query = "gadget", page = 2, pageSize = 10, sort = "price_asc")

        // When
        controller.search(request, correlationId = null, sessionId = "session-456")

        // Then
        val capturedQuery = querySlot.captured
        assertEquals("gadget", capturedQuery.query)
        assertEquals(2, capturedQuery.page)
        assertEquals(10, capturedQuery.pageSize)
        assertEquals(SortOption.PRICE_ASC, capturedQuery.sort)
    }

    @Test
    fun `search should default to RELEVANCE for invalid sort option`() {
        // Given
        val querySlot = slot<SearchQuery>()
        val searchResult = SearchResult(
            products = emptyList(),
            totalResults = 0L,
            page = 1,
            pageSize = 24,
            totalPages = 0,
            spellingSuggestion = null,
            executionTimeMs = 3
        )

        every { searchProductsUseCase.execute(capture(querySlot), any(), any()) } returns searchResult

        val request = SearchRequest(query = "test", sort = "invalid_sort")

        // When
        controller.search(request, correlationId = null, sessionId = null)

        // Then
        assertEquals(SortOption.RELEVANCE, querySlot.captured.sort)
    }

    @Test
    fun `search should pass correlation ID and session ID to use case`() {
        // Given
        val correlationId = UUID.randomUUID().toString()
        val sessionId = "session-789"
        val sessionSlot = slot<String?>()
        val correlationSlot = slot<UUID>()

        val searchResult = SearchResult(
            products = emptyList(),
            totalResults = 0L,
            page = 1,
            pageSize = 24,
            totalPages = 0,
            spellingSuggestion = null,
            executionTimeMs = 2
        )

        every {
            searchProductsUseCase.execute(any(), captureNullable(sessionSlot), capture(correlationSlot))
        } returns searchResult

        val request = SearchRequest(query = "test")

        // When
        controller.search(request, correlationId = correlationId, sessionId = sessionId)

        // Then
        assertEquals(sessionId, sessionSlot.captured)
        assertEquals(UUID.fromString(correlationId), correlationSlot.captured)
    }

    @Test
    fun `search should include spelling suggestion in response when present`() {
        // Given
        val searchResult = SearchResult(
            products = emptyList(),
            totalResults = 0L,
            page = 1,
            pageSize = 24,
            totalPages = 0,
            spellingSuggestion = "Premium Widget",
            executionTimeMs = 8
        )

        every { searchProductsUseCase.execute(any(), any(), any()) } returns searchResult

        val request = SearchRequest(query = "wiget")

        // When
        val response = controller.search(request, correlationId = null, sessionId = null)

        // Then
        val body = response.body
        assertNotNull(body)
        assertEquals("Premium Widget", body.spellingSuggestion)
        assertEquals(0L, body.totalResults)
    }

    @Test
    fun `search should generate new correlation ID for invalid UUID`() {
        // Given
        val correlationSlot = slot<UUID>()
        val searchResult = SearchResult(
            products = emptyList(),
            totalResults = 0L,
            page = 1,
            pageSize = 24,
            totalPages = 0,
            spellingSuggestion = null,
            executionTimeMs = 1
        )

        every { searchProductsUseCase.execute(any(), any(), capture(correlationSlot)) } returns searchResult

        val request = SearchRequest(query = "test")

        // When
        controller.search(request, correlationId = "not-a-uuid", sessionId = null)

        // Then
        assertNotNull(correlationSlot.captured)
    }
}
