package com.acme.product.application

import com.acme.product.domain.CatalogProduct
import com.acme.product.domain.ProductNotFoundException
import com.acme.product.domain.ProductSummary
import com.acme.product.domain.events.ProductViewed
import com.acme.product.infrastructure.messaging.ProductEventPublisher
import com.acme.product.infrastructure.projection.CatalogReadModelQueries
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class GetProductDetailUseCaseTest {

    private lateinit var queries: CatalogReadModelQueries
    private lateinit var eventPublisher: ProductEventPublisher
    private lateinit var useCase: GetProductDetailUseCase

    @BeforeEach
    fun setUp() {
        queries = mockk()
        eventPublisher = mockk()
        useCase = GetProductDetailUseCase(queries, eventPublisher)
    }

    private fun createProduct(
        id: UUID = UUID.randomUUID(),
        slug: String = "test-product",
        name: String = "Test Product",
        price: BigDecimal = BigDecimal("49.99"),
        category: String? = "Electronics",
        tags: String? = "gadget,tech"
    ) = CatalogProduct(
        id = id,
        slug = slug,
        name = name,
        description = null,
        price = price,
        category = category,
        tags = tags,
        inStock = true,
        imageUrl = null,
        variants = emptyList()
    )

    private fun summary(slug: String, imageUrl: String? = null) = ProductSummary(
        id = UUID.randomUUID(),
        slug = slug,
        name = slug,
        price = BigDecimal("19.99"),
        category = "Electronics",
        inStock = true,
        imageUrl = imageUrl
    )

    @Test
    fun `execute should return product detail for valid slug`() {
        val product = createProduct(slug = "premium-widget", name = "Premium Widget")

        every { queries.findBySlug("premium-widget") } returns product
        every { queries.findInStockRelated(any(), any(), any()) } returns emptyList()
        every { eventPublisher.publish(any()) } just Runs

        val result = useCase.execute("premium-widget")

        assertEquals(product, result.product)
        assertEquals(emptyList(), result.relatedProducts)
    }

    // The query finds published products only, so an archived product is an unknown slug too
    @Test
    fun `execute should throw ProductNotFoundException for unknown or archived slug`() {
        every { queries.findBySlug("unknown-slug") } returns null

        assertFailsWith<ProductNotFoundException> {
            useCase.execute("unknown-slug")
        }
        verify(exactly = 0) { eventPublisher.publish(any()) }
    }

    @Test
    fun `execute should return related products from same category`() {
        val product = createProduct(slug = "main-product", category = "Electronics")

        every { queries.findBySlug("main-product") } returns product
        every { queries.findInStockRelated("Electronics", product.id, any()) } returns listOf(summary("related-1"), summary("related-2"))
        every { eventPublisher.publish(any()) } just Runs

        val result = useCase.execute("main-product")

        assertEquals(listOf("related-1", "related-2"), result.relatedProducts.map { it.slug })
    }

    @Test
    fun `execute should return no related products when product has no category`() {
        val product = createProduct(slug = "no-category-product", category = null)

        every { queries.findBySlug("no-category-product") } returns product
        every { eventPublisher.publish(any()) } just Runs

        val result = useCase.execute("no-category-product")

        assertEquals(emptyList(), result.relatedProducts)
        verify(exactly = 0) { queries.findInStockRelated(any(), any(), any()) }
    }

    @Test
    fun `execute should publish ProductViewed event`() {
        val productId = UUID.randomUUID()
        val product = createProduct(id = productId, slug = "my-product")
        val eventSlot = slot<ProductViewed>()

        every { queries.findBySlug("my-product") } returns product
        every { queries.findInStockRelated(any(), any(), any()) } returns emptyList()
        every { eventPublisher.publish(capture(eventSlot)) } just Runs

        val correlationId = UUID.randomUUID()
        useCase.execute("my-product", sessionId = "session-xyz", correlationId = correlationId)

        verify(exactly = 1) { eventPublisher.publish(any()) }
        val event = eventSlot.captured
        assertEquals(productId, event.payload.productId)
        assertEquals("my-product", event.payload.slug)
        assertEquals("session-xyz", event.payload.sessionId)
        assertEquals(correlationId, event.correlationId)
    }

    @Test
    fun `execute should not fail when event publishing throws`() {
        val product = createProduct(slug = "resilient-product")

        every { queries.findBySlug("resilient-product") } returns product
        every { queries.findInStockRelated(any(), any(), any()) } returns emptyList()
        every { eventPublisher.publish(any()) } throws RuntimeException("Kafka unavailable")

        val result = useCase.execute("resilient-product")

        assertNotNull(result.product)
        assertEquals("resilient-product", result.product.slug)
    }

    // US-0004-10 AC-04 (PIN-273): the query returns only in-stock products, with their images, so
    // the use case asks for exactly 4
    @Test
    fun `execute should return up to 4 in-stock related products, with their images`() {
        val product = createProduct(slug = "main-product", category = "Electronics")
        every { queries.findBySlug("main-product") } returns product
        every { queries.findInStockRelated("Electronics", product.id, 4) } returns
            (1..4).map { summary("related-$it", imageUrl = if (it == 1) "https://img/1" else null) }
        every { eventPublisher.publish(any()) } just Runs

        val result = useCase.execute("main-product").relatedProducts

        assertEquals(listOf("related-1", "related-2", "related-3", "related-4"), result.map { it.slug })
        assertEquals("https://img/1", result.first().imageUrl)
    }
}
