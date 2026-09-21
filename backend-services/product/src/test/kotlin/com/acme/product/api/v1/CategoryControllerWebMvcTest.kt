package com.acme.product.api.v1

import com.acme.product.application.BrowseCategoriesUseCase
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * Web-layer tests for [CategoryController].
 *
 * Covers routing and the JSON contract the customer frontend's search-unavailable
 * fallback depends on (US-0004-09), including that the pagination query parameters
 * default when omitted.
 */
@WebMvcTest(CategoryController::class)
class CategoryControllerWebMvcTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val browseCategoriesUseCase: BrowseCategoriesUseCase
) {

    @TestConfiguration
    class MockBeans {
        @Bean
        fun browseCategoriesUseCase(): BrowseCategoriesUseCase = mockk()
    }

    @Test
    fun `GET categories should return the category list as JSON`() {
        // Given
        every { browseCategoriesUseCase.listCategories() } returns listOf(
            BrowseCategoriesUseCase.Category("Electronics", 5L)
        )

        // When / Then
        mockMvc.get("/api/v1/categories").andExpect {
            status { isOk() }
            jsonPath("$.categories[0].name") { value("Electronics") }
            jsonPath("$.categories[0].productCount") { value(5) }
        }
    }

    @Test
    fun `GET category products should default page and pageSize when omitted`() {
        // Given
        every { browseCategoriesUseCase.productsInCategory("Electronics", 1, 24) } returns
            BrowseCategoriesUseCase.CategoryProducts(
                category = "Electronics",
                products = emptyList(),
                totalResults = 0L,
                page = 1,
                pageSize = 24,
                totalPages = 0
            )

        // When / Then
        mockMvc.get("/api/v1/categories/Electronics/products").andExpect {
            status { isOk() }
            jsonPath("$.category") { value("Electronics") }
            jsonPath("$.page") { value(1) }
            jsonPath("$.pageSize") { value(24) }
            jsonPath("$.totalPages") { value(0) }
        }
    }

    @Test
    fun `GET category products should honour explicit pagination parameters`() {
        // Given
        every { browseCategoriesUseCase.productsInCategory("Apparel", 2, 10) } returns
            BrowseCategoriesUseCase.CategoryProducts(
                category = "Apparel",
                products = emptyList(),
                totalResults = 30L,
                page = 2,
                pageSize = 10,
                totalPages = 3
            )

        // When / Then
        mockMvc.get("/api/v1/categories/Apparel/products?page=2&pageSize=10").andExpect {
            status { isOk() }
            jsonPath("$.page") { value(2) }
            jsonPath("$.pageSize") { value(10) }
            jsonPath("$.totalResults") { value(30) }
        }
    }

    @Test
    fun `GET category products should decode a URL-encoded category name containing a space`() {
        // Given
        every { browseCategoriesUseCase.productsInCategory("Home Goods", 1, 24) } returns
            BrowseCategoriesUseCase.CategoryProducts(
                category = "Home Goods",
                products = emptyList(),
                totalResults = 0L,
                page = 1,
                pageSize = 24,
                totalPages = 0
            )

        // When / Then
        mockMvc.get("/api/v1/categories/{name}/products", "Home Goods").andExpect {
            status { isOk() }
            jsonPath("$.category") { value("Home Goods") }
        }
    }
}
