package com.acme.product.application

import com.acme.product.domain.CategoryCount
import com.acme.product.domain.ProductSummary
import com.acme.product.infrastructure.projection.CatalogReadModelQueries
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BrowseCategoriesUseCaseTest {

    private lateinit var queries: CatalogReadModelQueries
    private lateinit var useCase: BrowseCategoriesUseCase

    @BeforeEach
    fun setUp() {
        queries = mockk()
        useCase = BrowseCategoriesUseCase(queries)
    }

    private fun summary(
        id: UUID = UUID.randomUUID(),
        slug: String = "test-product",
        name: String = "Test Product",
        price: BigDecimal = BigDecimal("29.99"),
        inStock: Boolean = true,
        imageUrl: String? = null
    ) = ProductSummary(
        id = id,
        slug = slug,
        name = name,
        price = price,
        category = "Electronics",
        inStock = inStock,
        imageUrl = imageUrl
    )

    // ---------------------------------------------------------------------
    // listCategories
    // ---------------------------------------------------------------------

    @Test
    fun `listCategories should return the categories with their counts`() {
        // Given
        every { queries.listCategories() } returns listOf(CategoryCount("Apparel", 12L), CategoryCount("Electronics", 5L))

        // When
        val categories = useCase.listCategories()

        // Then
        assertEquals(2, categories.size)
        assertEquals("Apparel", categories[0].name)
        assertEquals(12L, categories[0].productCount)
        assertEquals("Electronics", categories[1].name)
        assertEquals(5L, categories[1].productCount)
    }

    @Test
    fun `listCategories should return empty list when no categories exist`() {
        // Given
        every { queries.listCategories() } returns emptyList()

        // When / Then
        assertTrue(useCase.listCategories().isEmpty())
    }

    // ---------------------------------------------------------------------
    // productsInCategory
    // ---------------------------------------------------------------------

    @Test
    fun `productsInCategory should return the products with pagination metadata`() {
        // Given
        val id = UUID.randomUUID()
        every { queries.countByCategory("Electronics") } returns 1L
        every { queries.findByCategory("Electronics", 1, 24) } returns listOf(
            summary(id = id, slug = "premium-widget", name = "Premium Widget", price = BigDecimal("49.99"))
        )

        // When
        val result = useCase.productsInCategory("Electronics")

        // Then
        assertEquals("Electronics", result.category)
        assertEquals(1L, result.totalResults)
        assertEquals(1, result.page)
        assertEquals(24, result.pageSize)
        assertEquals(1, result.totalPages)
        assertEquals(1, result.products.size)
        assertEquals(id, result.products[0].id)
        assertEquals("premium-widget", result.products[0].slug)
        assertEquals("Premium Widget", result.products[0].name)
        assertEquals(BigDecimal("49.99"), result.products[0].price)
        assertEquals("Electronics", result.products[0].category)
    }

    @Test
    fun `productsInCategory should pass the requested page and page size to the query`() {
        // Given
        every { queries.countByCategory("Electronics") } returns 100L
        every { queries.findByCategory("Electronics", 3, 10) } returns emptyList()

        // When
        useCase.productsInCategory("Electronics", page = 3, pageSize = 10)

        // Then
        verify(exactly = 1) { queries.findByCategory("Electronics", 3, 10) }
    }

    @Test
    fun `productsInCategory should round totalPages up on a partial last page`() {
        // Given
        every { queries.countByCategory("Electronics") } returns 25L
        every { queries.findByCategory("Electronics", any(), any()) } returns emptyList()

        // When
        val result = useCase.productsInCategory("Electronics", page = 1, pageSize = 24)

        // Then
        assertEquals(2, result.totalPages)
    }

    @Test
    fun `productsInCategory should report zero totalPages for an empty category`() {
        // Given
        every { queries.countByCategory("Unknown") } returns 0L
        every { queries.findByCategory("Unknown", any(), any()) } returns emptyList()

        // When
        val result = useCase.productsInCategory("Unknown")

        // Then
        assertEquals(0L, result.totalResults)
        assertEquals(0, result.totalPages)
        assertTrue(result.products.isEmpty())
    }

    @Test
    fun `productsInCategory should reject a page below 1`() {
        assertFailsWith<IllegalArgumentException> {
            useCase.productsInCategory("Electronics", page = 0)
        }
    }

    @Test
    fun `productsInCategory should reject a pageSize above 100`() {
        assertFailsWith<IllegalArgumentException> {
            useCase.productsInCategory("Electronics", pageSize = 101)
        }
    }

    // US-0004-10 (PIN-273): the category fallback's cards carry stock and image like search's; the
    // read model holds both on each product
    @Test
    fun `productsInCategory should give each product its stock and image`() {
        every { queries.countByCategory("Electronics") } returns 2L
        every { queries.findByCategory("Electronics", 1, 24) } returns listOf(
            summary(slug = "out", inStock = false),
            summary(slug = "in", inStock = true, imageUrl = "https://img/in")
        )

        val products = useCase.productsInCategory("Electronics").products

        assertEquals(listOf(false, true), products.map { it.inStock })
        assertEquals(listOf(null, "https://img/in"), products.map { it.imageUrl })
    }
}
