package com.acme.product.application

import com.acme.product.infrastructure.persistence.AutocompleteProductProjection
import com.acme.product.infrastructure.persistence.ProductRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutocompleteUseCaseTest {

    private lateinit var repository: ProductRepository
    private lateinit var useCase: AutocompleteUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = AutocompleteUseCase(repository)
    }

    private fun createProductProjection(
        id: UUID = UUID.randomUUID(),
        name: String = "Test Product",
        slug: String = "test-product"
    ): AutocompleteProductProjection {
        val projection = mockk<AutocompleteProductProjection>()
        every { projection.getId() } returns id
        every { projection.getName() } returns name
        every { projection.getSlug() } returns slug
        return projection
    }

    @Test
    fun `execute should return product and category suggestions`() {
        val productId = UUID.randomUUID()
        val product = createProductProjection(id = productId, name = "Wireless Router", slug = "wireless-router")

        every { repository.autocompleteProducts("wir", 5) } returns listOf(product)
        every { repository.autocompleteCategories("wir", 3) } returns listOf("Wires & Cables")

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
        assertEquals("wires-&-cables", categorySuggestion.categorySlug)
    }

    @Test
    fun `execute should return empty suggestions when no matches`() {
        every { repository.autocompleteProducts("xyz", 5) } returns emptyList()
        every { repository.autocompleteCategories("xyz", 3) } returns emptyList()

        val result = useCase.execute("xyz")

        assertEquals("xyz", result.query)
        assertTrue(result.suggestions.isEmpty())
    }

    @Test
    fun `execute should cap total suggestions at limit`() {
        val products = (1..5).map { createProductProjection(name = "Product $it", slug = "product-$it") }
        val categories = listOf("Category A", "Category B", "Category C")

        every { repository.autocompleteProducts("pro", 4) } returns products.take(4)
        every { repository.autocompleteCategories("pro", 3) } returns categories

        val result = useCase.execute("pro", limit = 4)

        assertEquals(4, result.suggestions.size)
    }

    @Test
    fun `execute should place products before categories`() {
        val product = createProductProjection(name = "Widget Pro", slug = "widget-pro")

        every { repository.autocompleteProducts("wi", 5) } returns listOf(product)
        every { repository.autocompleteCategories("wi", 3) } returns listOf("Widgets")

        val result = useCase.execute("wi")

        assertEquals("product", result.suggestions[0].type)
        assertEquals("category", result.suggestions[1].type)
    }

    @Test
    fun `execute should generate category slug from category name`() {
        every { repository.autocompleteProducts("el", 5) } returns emptyList()
        every { repository.autocompleteCategories("el", 3) } returns listOf("Electronics")

        val result = useCase.execute("el")

        assertEquals(1, result.suggestions.size)
        assertEquals("electronics", result.suggestions[0].categorySlug)
    }
}
