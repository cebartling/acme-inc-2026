package com.acme.product.api.v1

import com.acme.product.application.AutocompleteUseCase
import com.acme.product.application.SearchProductsUseCase
import com.acme.product.domain.SearchQuery
import com.acme.product.domain.SearchResult
import com.acme.product.domain.SortOption
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import com.acme.product.domain.ProductSummary
import java.math.BigDecimal
import java.util.UUID
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

    // The mock bean is shared across tests, so verify(exactly = 0) must not see earlier calls
    @BeforeEach
    fun setUp() {
        clearMocks(searchProductsUseCase)
    }

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

    // PIN-302: 24.9 used to be truncated to 24 and accepted. The check is on the JSON token,
    // so 24.0 is rejected too. Stubbed so a regression fails on the status, not on a missing
    // MockK answer.
    @ParameterizedTest
    @ValueSource(strings = ["\"pageSize\":24.9", "\"pageSize\":24.0", "\"page\":1.5"])
    fun `a decimal page or pageSize is rejected, not truncated`(field: String) {
        every { searchProductsUseCase.execute(any(), any(), any()) } returns emptyResult

        mockMvc.post("/api/v1/search") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"query":"widget",$field}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Invalid request: ${field.substringBefore(':').trim('"')}") }
            jsonPath("$.code") { value(GlobalExceptionHandler.INVALID_REQUEST) }
        }

        verify(exactly = 0) { searchProductsUseCase.execute(any(), any(), any()) }
    }

    // PIN-303: validation and deserialization 400s share the {error, code} body
    @Test
    fun `a pageSize over the max is a 400 INVALID_REQUEST naming the field`() {
        mockMvc.post("/api/v1/search") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"query":"widget","pageSize":101}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Invalid request: pageSize") }
            jsonPath("$.code") { value(GlobalExceptionHandler.INVALID_REQUEST) }
        }
    }

    @Test
    fun `a missing query is a 400 INVALID_REQUEST naming the field`() {
        mockMvc.post("/api/v1/search") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"pageSize":10}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Invalid request: query") }
            jsonPath("$.code") { value(GlobalExceptionHandler.INVALID_REQUEST) }
        }
    }

    @Test
    fun `a comma in a category name is a 400 INVALID_REQUEST`() {
        mockMvc.post("/api/v1/search") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"query":"widget","filters":{"categories":["a,b"]}}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(GlobalExceptionHandler.INVALID_REQUEST) }
        }
    }

    @Test
    fun `malformed JSON is a 400 INVALID_REQUEST without parser details`() {
        mockMvc.post("/api/v1/search") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"query":"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Invalid request") }
            jsonPath("$.code") { value(GlobalExceptionHandler.INVALID_REQUEST) }
        }
    }

    // PIN-305: query-parameter 400s name the parameter as sent (q, not the Kotlin `query`) and
    // never echo the conversion message
    @ParameterizedTest
    @CsvSource(
        "q=a, q",                 // @Size(min = 2)
        "limit=8, q",             // missing q
        "q=ab&limit=abc, limit",  // not a number
        "q=ab&limit=21, limit"    // @Max(20)
    )
    fun `an invalid autocomplete parameter is a 400 INVALID_REQUEST naming it`(params: String, name: String) {
        mockMvc.get("/api/v1/search/autocomplete?$params").andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Invalid request: $name") }
            jsonPath("$.code") { value(GlobalExceptionHandler.INVALID_REQUEST) }
        }
    }

    // US-0004-10 AC-06 (PIN-273): the card needs each result's stock and image
    @Test
    fun `search results carry inStock and imageUrl`() {
        val id = UUID.randomUUID()
        every { searchProductsUseCase.execute(any(), any(), any()) } returns emptyResult.copy(
            products = listOf(
                ProductSummary(id, "old-widget", "Old Widget", BigDecimal("9.99"), inStock = false, imageUrl = "https://img/w")
            ),
            totalResults = 1L
        )

        mockMvc.post("/api/v1/search") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"query":"widget"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.results[0].inStock") { value(false) }
            jsonPath("$.results[0].imageUrl") { value("https://img/w") }
        }
    }
}
