package com.acme.product.api.v1

import com.acme.product.application.AutocompleteUseCase
import com.acme.product.application.SearchProductsUseCase
import com.acme.product.domain.SearchQuery
import com.acme.product.domain.SearchResult
import com.acme.product.domain.SortOption
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals

/**
 * Web-layer tests for [SearchController] that go through real JSON deserialization.
 *
 * Guards PIN-250: Kotlin default values in [SearchRequest] must apply when the
 * client omits those fields, which requires the Jackson 3 Kotlin module.
 */
@WebMvcTest(SearchController::class)
class SearchControllerWebMvcTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val searchProductsUseCase: SearchProductsUseCase
) {

    @TestConfiguration
    class MockBeans {
        @Bean
        fun searchProductsUseCase(): SearchProductsUseCase = mockk()

        @Bean
        fun autocompleteUseCase(): AutocompleteUseCase = mockk()
    }

    private val emptyResult = SearchResult(
        products = emptyList(), totalResults = 0L, page = 1, pageSize = 24, totalPages = 0,
        spellingSuggestion = null, executionTimeMs = 1
    )

    @Test
    fun `search should apply Kotlin defaults when optional fields are omitted`() {
        // Given
        val querySlot = slot<SearchQuery>()
        every { searchProductsUseCase.execute(capture(querySlot), any(), any()) } returns emptyResult

        // When
        mockMvc.post("/api/v1/search") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"query":"widget"}"""
        }.andExpect {
            status { isOk() }
        }

        // Then
        val captured = querySlot.captured
        assertEquals("widget", captured.query)
        assertEquals(1, captured.page)
        assertEquals(24, captured.pageSize)
        assertEquals(SortOption.RELEVANCE, captured.sort)
        assertEquals(emptyList(), captured.filters.categories)
    }

    @Test
    fun `search should apply filter defaults when filters object is empty`() {
        // Given
        val querySlot = slot<SearchQuery>()
        every { searchProductsUseCase.execute(capture(querySlot), any(), any()) } returns emptyResult

        // When
        mockMvc.post("/api/v1/search") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"query":"widget","filters":{}}"""
        }.andExpect {
            status { isOk() }
        }

        // Then
        assertEquals(emptyList(), querySlot.captured.filters.categories)
    }
}
