package com.acme.product.api.v1

import com.acme.product.application.BrowseCategoriesUseCase
import com.acme.product.domain.ProductSummary
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CategoryControllerTest {

    private lateinit var browseCategoriesUseCase: BrowseCategoriesUseCase
    private lateinit var controller: CategoryController

    @BeforeEach
    fun setUp() {
        browseCategoriesUseCase = mockk()
        controller = CategoryController(browseCategoriesUseCase)
    }

    @Test
    fun `listCategories should return 200 with categories and counts`() {
        // Given
        every { browseCategoriesUseCase.listCategories() } returns listOf(
            BrowseCategoriesUseCase.Category("Apparel", 12L),
            BrowseCategoriesUseCase.Category("Electronics", 5L)
        )

        // When
        val response = controller.listCategories()

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals(2, body.categories.size)
        assertEquals("Apparel", body.categories[0].name)
        assertEquals(12L, body.categories[0].productCount)
        assertEquals("Electronics", body.categories[1].name)
        assertEquals(5L, body.categories[1].productCount)
    }

    @Test
    fun `listCategories should return 200 with an empty list when no categories exist`() {
        // Given
        every { browseCategoriesUseCase.listCategories() } returns emptyList()

        // When
        val response = controller.listCategories()

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(assertNotNull(response.body).categories.isEmpty())
    }

    @Test
    fun `productsInCategory should return 200 with correct response shape`() {
        // Given
        val productId = UUID.randomUUID()
        every { browseCategoriesUseCase.productsInCategory("Electronics", 1, 24) } returns
            BrowseCategoriesUseCase.CategoryProducts(
                category = "Electronics",
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
                totalPages = 1
            )

        // When
        val response = controller.productsInCategory("Electronics", 1, 24)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals("Electronics", body.category)
        assertEquals(1L, body.totalResults)
        assertEquals(1, body.page)
        assertEquals(24, body.pageSize)
        assertEquals(1, body.totalPages)
        assertEquals(1, body.results.size)
        assertEquals(productId, body.results[0].id)
        assertEquals("premium-widget", body.results[0].slug)
        assertEquals("Premium Widget", body.results[0].name)
        assertEquals(BigDecimal("49.99"), body.results[0].price)
        assertEquals("Electronics", body.results[0].category)
    }

    @Test
    fun `productsInCategory should pass pagination parameters through to the use case`() {
        // Given
        every { browseCategoriesUseCase.productsInCategory("Apparel", 3, 10) } returns
            BrowseCategoriesUseCase.CategoryProducts(
                category = "Apparel",
                products = emptyList(),
                totalResults = 100L,
                page = 3,
                pageSize = 10,
                totalPages = 10
            )

        // When
        controller.productsInCategory("Apparel", 3, 10)

        // Then
        verify(exactly = 1) { browseCategoriesUseCase.productsInCategory("Apparel", 3, 10) }
    }
}
