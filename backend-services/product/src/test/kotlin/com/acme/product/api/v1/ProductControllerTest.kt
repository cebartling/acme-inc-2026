package com.acme.product.api.v1

import com.acme.product.application.GetProductDetailUseCase
import com.acme.product.domain.Product
import com.acme.product.domain.ProductNotFoundException
import com.acme.product.domain.ProductStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ProductControllerTest {

    private lateinit var getProductDetailUseCase: GetProductDetailUseCase
    private lateinit var controller: ProductController

    @BeforeEach
    fun setUp() {
        getProductDetailUseCase = mockk()
        controller = ProductController(getProductDetailUseCase)
    }

    @Test
    fun `getProduct should return 200 with correct response shape`() {
        // Given
        val productId = UUID.randomUUID()
        val relatedId = UUID.randomUUID()
        val product = Product(
            id = productId,
            slug = "premium-widget",
            name = "Premium Widget",
            description = "A great widget",
            price = BigDecimal("49.99"),
            status = ProductStatus.PUBLISHED,
            category = "Electronics",
            tags = "sale, featured, new"
        )
        val relatedProduct = Product(
            id = relatedId,
            slug = "basic-widget",
            name = "Basic Widget",
            price = BigDecimal("19.99"),
            category = "Electronics"
        )
        every { getProductDetailUseCase.execute(any(), any(), any()) } returns
            GetProductDetailUseCase.Result(product = product, relatedProducts = listOf(relatedProduct))

        // When
        val response = controller.getProduct(slug = "premium-widget", sessionId = null, correlationId = null)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body
        assertNotNull(body)
        assertEquals(productId, body.id)
        assertEquals("premium-widget", body.slug)
        assertEquals("Premium Widget", body.name)
        assertEquals("A great widget", body.description)
        assertEquals(BigDecimal("49.99"), body.price)
        assertEquals("Electronics", body.category)
        assertEquals(listOf("sale", "featured", "new"), body.tags)
        assertEquals("IN_STOCK", body.availability)
        assertEquals(1, body.relatedProducts.size)
        assertEquals(relatedId, body.relatedProducts[0].id)
        assertEquals("basic-widget", body.relatedProducts[0].slug)
        assertEquals("Basic Widget", body.relatedProducts[0].name)
        assertEquals(BigDecimal("19.99"), body.relatedProducts[0].price)
        assertEquals("Electronics", body.relatedProducts[0].category)
    }

    @Test
    fun `getProduct should map ARCHIVED status to OUT_OF_STOCK availability`() {
        // Given
        val product = Product(
            id = UUID.randomUUID(),
            slug = "discontinued-widget",
            name = "Discontinued Widget",
            price = BigDecimal("9.99"),
            status = ProductStatus.ARCHIVED
        )
        every { getProductDetailUseCase.execute(any(), any(), any()) } returns
            GetProductDetailUseCase.Result(product = product, relatedProducts = emptyList())

        // When
        val response = controller.getProduct(slug = "discontinued-widget", sessionId = null, correlationId = null)

        // Then
        assertEquals("OUT_OF_STOCK", response.body?.availability)
    }

    @Test
    fun `getProduct should pass slug and session ID to use case`() {
        // Given
        val slugSlot = slot<String>()
        val sessionSlot = slot<String>()
        val product = Product(
            id = UUID.randomUUID(),
            slug = "test-slug",
            name = "Test Product",
            price = BigDecimal("10.00")
        )
        every {
            getProductDetailUseCase.execute(capture(slugSlot), capture(sessionSlot), any())
        } returns GetProductDetailUseCase.Result(product = product, relatedProducts = emptyList())

        // When
        controller.getProduct(slug = "test-slug", sessionId = "session-abc", correlationId = null)

        // Then
        assertEquals("test-slug", slugSlot.captured)
        assertEquals("session-abc", sessionSlot.captured)
    }

    @Test
    fun `getProduct should pass parsed correlation ID to use case`() {
        // Given
        val correlationId = UUID.randomUUID()
        val correlationSlot = slot<UUID>()
        val product = Product(
            id = UUID.randomUUID(),
            slug = "test-slug",
            name = "Test Product",
            price = BigDecimal("10.00")
        )
        every {
            getProductDetailUseCase.execute(any(), any(), capture(correlationSlot))
        } returns GetProductDetailUseCase.Result(product = product, relatedProducts = emptyList())

        // When
        controller.getProduct(slug = "test-slug", sessionId = null, correlationId = correlationId.toString())

        // Then
        assertEquals(correlationId, correlationSlot.captured)
    }

    @Test
    fun `getProduct should generate new UUID for invalid correlation ID`() {
        // Given
        val correlationSlot = slot<UUID>()
        val product = Product(
            id = UUID.randomUUID(),
            slug = "test-slug",
            name = "Test Product",
            price = BigDecimal("10.00")
        )
        every {
            getProductDetailUseCase.execute(any(), any(), capture(correlationSlot))
        } returns GetProductDetailUseCase.Result(product = product, relatedProducts = emptyList())

        // When
        controller.getProduct(slug = "test-slug", sessionId = null, correlationId = "not-a-uuid")

        // Then
        assertNotNull(correlationSlot.captured)
    }

    @Test
    fun `getProduct should propagate ProductNotFoundException for unknown slug`() {
        // Given
        every {
            getProductDetailUseCase.execute(any(), any(), any())
        } throws ProductNotFoundException("unknown-slug")

        // When / Then
        assertThrows<ProductNotFoundException> {
            controller.getProduct(slug = "unknown-slug", sessionId = null, correlationId = null)
        }
    }

    @Test
    fun `getProduct should return empty tags list when product has no tags`() {
        // Given
        val product = Product(
            id = UUID.randomUUID(),
            slug = "no-tags-widget",
            name = "No Tags Widget",
            price = BigDecimal("25.00"),
            tags = null
        )
        every {
            getProductDetailUseCase.execute(any(), any(), any())
        } returns GetProductDetailUseCase.Result(product = product, relatedProducts = emptyList())

        // When
        val response = controller.getProduct(slug = "no-tags-widget", sessionId = null, correlationId = null)

        // Then
        assertEquals(emptyList(), response.body?.tags)
    }
}
