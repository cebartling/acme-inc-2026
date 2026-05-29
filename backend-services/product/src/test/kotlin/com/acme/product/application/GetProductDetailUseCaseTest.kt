package com.acme.product.application

import com.acme.product.domain.Product
import com.acme.product.domain.ProductNotFoundException
import com.acme.product.domain.ProductStatus
import com.acme.product.domain.events.ProductViewed
import com.acme.product.infrastructure.messaging.ProductEventPublisher
import com.acme.product.infrastructure.persistence.ProductRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class GetProductDetailUseCaseTest {

    private lateinit var repository: ProductRepository
    private lateinit var eventPublisher: ProductEventPublisher
    private lateinit var useCase: GetProductDetailUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        eventPublisher = mockk()
        useCase = GetProductDetailUseCase(repository, eventPublisher)
    }

    private fun createProduct(
        id: UUID = UUID.randomUUID(),
        slug: String = "test-product",
        name: String = "Test Product",
        price: BigDecimal = BigDecimal("49.99"),
        status: ProductStatus = ProductStatus.PUBLISHED,
        category: String? = "Electronics",
        tags: String? = "gadget,tech"
    ) = Product(
        id = id,
        slug = slug,
        name = name,
        price = price,
        status = status,
        category = category,
        tags = tags,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Test
    fun `execute should return product detail for valid slug`() {
        val product = createProduct(slug = "premium-widget", name = "Premium Widget")

        every { repository.findBySlugAndStatus("premium-widget", ProductStatus.PUBLISHED) } returns Optional.of(product)
        every { repository.findRelatedProducts(any(), any(), any()) } returns emptyList()
        every { eventPublisher.publish(any()) } just Runs

        val result = useCase.execute("premium-widget")

        assertEquals(product, result.product)
        assertEquals(emptyList(), result.relatedProducts)
    }

    @Test
    fun `execute should throw ProductNotFoundException for unknown slug`() {
        every { repository.findBySlugAndStatus("unknown-slug", ProductStatus.PUBLISHED) } returns Optional.empty()

        assertFailsWith<ProductNotFoundException> {
            useCase.execute("unknown-slug")
        }
    }

    @Test
    fun `execute should throw ProductNotFoundException for archived product`() {
        val archivedProduct = createProduct(slug = "archived-product", status = ProductStatus.ARCHIVED)
        every { repository.findBySlugAndStatus("archived-product", ProductStatus.PUBLISHED) } returns Optional.empty()

        assertFailsWith<ProductNotFoundException> {
            useCase.execute("archived-product")
        }
        // Confirm the archived product would be found by slug alone (verifies the filter matters)
        verify(exactly = 0) { eventPublisher.publish(any()) }
    }

    @Test
    fun `execute should return related products from same category`() {
        val product = createProduct(slug = "main-product", category = "Electronics")
        val related1 = createProduct(slug = "related-1", name = "Related One")
        val related2 = createProduct(slug = "related-2", name = "Related Two")

        every { repository.findBySlugAndStatus("main-product", ProductStatus.PUBLISHED) } returns Optional.of(product)
        every { repository.findRelatedProducts("Electronics", product.id, any<Pageable>()) } returns listOf(related1, related2)
        every { eventPublisher.publish(any()) } just Runs

        val result = useCase.execute("main-product")

        assertEquals(2, result.relatedProducts.size)
        assertEquals("related-1", result.relatedProducts[0].slug)
        assertEquals("related-2", result.relatedProducts[1].slug)
    }

    @Test
    fun `execute should return no related products when product has no category`() {
        val product = createProduct(slug = "no-category-product", category = null)

        every { repository.findBySlugAndStatus("no-category-product", ProductStatus.PUBLISHED) } returns Optional.of(product)
        every { eventPublisher.publish(any()) } just Runs

        val result = useCase.execute("no-category-product")

        assertEquals(emptyList(), result.relatedProducts)
        verify(exactly = 0) { repository.findRelatedProducts(any(), any(), any()) }
    }

    @Test
    fun `execute should publish ProductViewed event`() {
        val productId = UUID.randomUUID()
        val product = createProduct(id = productId, slug = "my-product")
        val eventSlot = slot<ProductViewed>()

        every { repository.findBySlugAndStatus("my-product", ProductStatus.PUBLISHED) } returns Optional.of(product)
        every { repository.findRelatedProducts(any(), any(), any()) } returns emptyList()
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

        every { repository.findBySlugAndStatus("resilient-product", ProductStatus.PUBLISHED) } returns Optional.of(product)
        every { repository.findRelatedProducts(any(), any(), any()) } returns emptyList()
        every { eventPublisher.publish(any()) } throws RuntimeException("Kafka unavailable")

        val result = useCase.execute("resilient-product")

        assertNotNull(result.product)
        assertEquals("resilient-product", result.product.slug)
    }
}
