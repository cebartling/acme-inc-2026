package com.acme.product.application

import com.acme.product.domain.ProductSuggestion
import com.acme.product.infrastructure.projection.CatalogSearchQueries
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutocompleteUseCaseTest {

    private lateinit var queries: CatalogSearchQueries
    private lateinit var useCase: AutocompleteUseCase

    @BeforeEach
    fun setUp() {
        queries = mockk()
        useCase = AutocompleteUseCase(queries)
    }

    private fun createProduct(
        id: UUID = UUID.randomUUID(),
        name: String = "Test Product",
        slug: String = "test-product"
    ) = ProductSuggestion(id, name, slug)

    @Test
    fun `execute should return product and category suggestions`() {
        val productId = UUID.randomUUID()
        val product = createProduct(id = productId, name = "Wireless Router", slug = "wireless-router")

        every { queries.autocompleteProducts("wir", 5) } returns listOf(product)
        every { queries.autocompleteCategories("wir", 3) } returns listOf("Wires & Cables")

        val result = useCase.execute("wir")

        assertEquals("wir", result.query)
        assertEquals(2, result.suggestions.size)

        val productSuggestion = result.suggestions[0]
        assertEquals("product", productSuggestion.type)
        assertEquals("Wireless Router", productSuggestion.text)
        assertEquals(productId, productSuggestion.productId)
        assertEquals("wireless-router", productSuggestion.productSlug)

        val categorySuggestion = result.suggestions[1]
        assertEquals("category", categorySuggestion.type)
        assertEquals("Wires & Cables", categorySuggestion.text)
        assertEquals("wires-cables", categorySuggestion.categorySlug)
    }

    @Test
    fun `execute should return empty suggestions when no matches`() {
        every { queries.autocompleteProducts("xyz", 5) } returns emptyList()
        every { queries.autocompleteCategories("xyz", 3) } returns emptyList()

        val result = useCase.execute("xyz")

        assertEquals("xyz", result.query)
        assertTrue(result.suggestions.isEmpty())
    }

    @Test
    fun `execute should cap total suggestions at limit`() {
        val products = (1..5).map { createProduct(name = "Product $it", slug = "product-$it") }
        val categories = listOf("Category A", "Category B", "Category C")

        every { queries.autocompleteProducts("pro", 4) } returns products.take(4)
        every { queries.autocompleteCategories("pro", 3) } returns categories

        val result = useCase.execute("pro", limit = 4)

        assertEquals(4, result.suggestions.size)
    }

    @Test
    fun `execute should place products before categories`() {
        val product = createProduct(name = "Widget Pro", slug = "widget-pro")

        every { queries.autocompleteProducts("wi", 5) } returns listOf(product)
        every { queries.autocompleteCategories("wi", 3) } returns listOf("Widgets")

        val result = useCase.execute("wi")

        assertEquals("product", result.suggestions[0].type)
        assertEquals("category", result.suggestions[1].type)
    }

    @Test
    fun `execute should generate category slug from category name`() {
        every { queries.autocompleteProducts("el", 5) } returns emptyList()
        every { queries.autocompleteCategories("el", 3) } returns listOf("Electronics")

        val result = useCase.execute("el")

        assertEquals(1, result.suggestions.size)
        assertEquals("electronics", result.suggestions[0].categorySlug)
    }
}
