package com.acme.product.application

import com.acme.product.domain.Product
import com.acme.product.domain.ProductStatus
import com.acme.product.infrastructure.persistence.CategoryFacetProjection
import com.acme.product.infrastructure.persistence.ProductRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BrowseCategoriesUseCaseTest {

    private lateinit var repository: ProductRepository
    private lateinit var useCase: BrowseCategoriesUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = BrowseCategoriesUseCase(repository)
    }

    private fun createFacetProjection(category: String, count: Long): CategoryFacetProjection {
        val projection = mockk<CategoryFacetProjection>()
        every { projection.getCategory() } returns category
        every { projection.getCount() } returns count
        return projection
    }

    private fun createProduct(
        id: UUID = UUID.randomUUID(),
        slug: String = "test-product",
        name: String = "Test Product",
        price: BigDecimal = BigDecimal("29.99"),
        category: String? = "Electronics"
    ) = Product(
        id = id,
        slug = slug,
        name = name,
        price = price,
        status = ProductStatus.PUBLISHED,
        category = category,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    // ---------------------------------------------------------------------
    // listCategories
    // ---------------------------------------------------------------------

    @Test
    fun `listCategories should map projections to categories with counts`() {
        // Given
        every { repository.findDistinctCategories() } returns listOf(
            createFacetProjection("Apparel", 12L),
            createFacetProjection("Electronics", 5L)
        )

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
        every { repository.findDistinctCategories() } returns emptyList()

        // When / Then
        assertTrue(useCase.listCategories().isEmpty())
    }

    // ---------------------------------------------------------------------
    // productsInCategory
    // ---------------------------------------------------------------------

    @Test
    fun `productsInCategory should return mapped products with pagination metadata`() {
        // Given
        val id = UUID.randomUUID()
        every { repository.countByCategory("Electronics") } returns 1L
        every { repository.findByCategory("Electronics", any()) } returns listOf(
            createProduct(id = id, slug = "premium-widget", name = "Premium Widget", price = BigDecimal("49.99"))
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
    fun `productsInCategory should convert 1-based page to 0-based pageable`() {
        // Given
        val pageableSlot = slot<Pageable>()
        every { repository.countByCategory("Electronics") } returns 100L
        every { repository.findByCategory("Electronics", capture(pageableSlot)) } returns emptyList()

        // When
        useCase.productsInCategory("Electronics", page = 3, pageSize = 10)

        // Then
        assertEquals(2, pageableSlot.captured.pageNumber)
        assertEquals(10, pageableSlot.captured.pageSize)
        verify(exactly = 1) { repository.findByCategory("Electronics", any()) }
    }

    @Test
    fun `productsInCategory should round totalPages up on a partial last page`() {
        // Given
        every { repository.countByCategory("Electronics") } returns 25L
        every { repository.findByCategory("Electronics", any()) } returns emptyList()

        // When
        val result = useCase.productsInCategory("Electronics", page = 1, pageSize = 24)

        // Then
        assertEquals(2, result.totalPages)
    }

    @Test
    fun `productsInCategory should report zero totalPages for an empty category`() {
        // Given
        every { repository.countByCategory("Unknown") } returns 0L
        every { repository.findByCategory("Unknown", any()) } returns emptyList()

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
}
