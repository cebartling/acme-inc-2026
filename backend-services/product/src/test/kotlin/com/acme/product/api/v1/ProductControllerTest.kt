package com.acme.product.api.v1

import com.acme.product.application.GetProductDetailUseCase
import com.acme.product.domain.CatalogProduct
import com.acme.product.domain.CatalogVariant
import com.acme.product.domain.ProductNotFoundException
import com.acme.product.domain.ProductSummary
import com.acme.product.domain.TierPrice
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProductControllerTest {

    private lateinit var getProductDetailUseCase: GetProductDetailUseCase
    private lateinit var controller: ProductController

    @BeforeEach
    fun setUp() {
        getProductDetailUseCase = mockk()
        controller = ProductController(getProductDetailUseCase)
    }

    private fun product(
        id: UUID = UUID.randomUUID(),
        slug: String = "test-slug",
        name: String = "Test Product",
        description: String? = null,
        price: String = "10.00",
        category: String? = null,
        tags: String? = null,
        inStock: Boolean = true,
        variants: List<CatalogVariant> = emptyList()
    ) = CatalogProduct(
        id = id,
        slug = slug,
        name = name,
        description = description,
        price = BigDecimal(price),
        category = category,
        tags = tags,
        inStock = inStock,
        imageUrl = null,
        variants = variants
    )

    private fun variant(
        id: UUID = UUID.randomUUID(),
        inStock: Boolean = true,
        images: List<String> = emptyList(),
        tierPricing: List<TierPrice> = emptyList()
    ) = CatalogVariant(
        id = id,
        sku = "ACME-GP-BLK",
        name = "Black",
        color = "Black",
        size = null,
        isDefault = true,
        inStock = inStock,
        priceOverride = null,
        images = images,
        tierPricing = tierPricing
    )

    private fun returning(product: CatalogProduct, related: List<ProductSummary> = emptyList()) {
        every { getProductDetailUseCase.execute(any(), any(), any()) } returns
            GetProductDetailUseCase.Result(product = product, relatedProducts = related)
    }

    @Test
    fun `getProduct should return 200 with correct response shape`() {
        // Given
        val productId = UUID.randomUUID()
        val relatedId = UUID.randomUUID()
        returning(
            product(
                id = productId,
                slug = "premium-widget",
                name = "Premium Widget",
                description = "A great widget",
                price = "49.99",
                category = "Electronics",
                tags = "sale, featured, new"
            ),
            related = listOf(
                ProductSummary(
                    id = relatedId,
                    slug = "basic-widget",
                    name = "Basic Widget",
                    price = BigDecimal("19.99"),
                    category = "Electronics",
                    imageUrl = "https://img/basic-widget"
                )
            )
        )

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
        // No variants counts as in stock, as in the summaries (PIN-318)
        assertEquals("IN_STOCK", body.availability)
        assertEquals(1, body.relatedProducts.size)
        assertEquals(relatedId, body.relatedProducts[0].id)
        assertEquals("basic-widget", body.relatedProducts[0].slug)
        assertEquals("Basic Widget", body.relatedProducts[0].name)
        assertEquals(BigDecimal("19.99"), body.relatedProducts[0].price)
        assertEquals("Electronics", body.relatedProducts[0].category)
        assertEquals(true, body.relatedProducts[0].inStock)
        assertEquals("https://img/basic-widget", body.relatedProducts[0].imageUrl)
    }

    @Test
    fun `getProduct should pass slug and session ID to use case`() {
        // Given
        val slugSlot = slot<String>()
        val sessionSlot = slot<String>()
        every {
            getProductDetailUseCase.execute(capture(slugSlot), capture(sessionSlot), any())
        } returns GetProductDetailUseCase.Result(product = product(), relatedProducts = emptyList())

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
        every {
            getProductDetailUseCase.execute(any(), any(), capture(correlationSlot))
        } returns GetProductDetailUseCase.Result(product = product(), relatedProducts = emptyList())

        // When
        controller.getProduct(slug = "test-slug", sessionId = null, correlationId = correlationId.toString())

        // Then
        assertEquals(correlationId, correlationSlot.captured)
    }

    @Test
    fun `getProduct should generate new UUID for invalid correlation ID`() {
        // Given
        val correlationSlot = slot<UUID>()
        every {
            getProductDetailUseCase.execute(any(), any(), capture(correlationSlot))
        } returns GetProductDetailUseCase.Result(product = product(), relatedProducts = emptyList())

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
        returning(product(slug = "no-tags-widget", tags = null))

        val response = controller.getProduct(slug = "no-tags-widget", sessionId = null, correlationId = null)

        assertEquals(emptyList(), response.body?.tags)
    }

    @Test
    fun `getProduct should map variants with images and tier pricing into response`() {
        // Given
        val variantId = UUID.randomUUID()
        returning(
            product(
                slug = "gadget-pro",
                variants = listOf(
                    variant(
                        id = variantId,
                        images = listOf("https://example.com/black.jpg"),
                        tierPricing = listOf(TierPrice(3, BigDecimal("109.99")))
                    )
                )
            )
        )

        // When
        val response = controller.getProduct(slug = "gadget-pro", sessionId = null, correlationId = null)

        // Then
        val body = response.body
        assertNotNull(body)
        assertEquals(1, body.variants.size)
        val variantResponse = body.variants[0]
        assertEquals(variantId, variantResponse.id)
        assertEquals("ACME-GP-BLK", variantResponse.sku)
        assertEquals("Black", variantResponse.name)
        assertEquals("Black", variantResponse.color)
        assertNull(variantResponse.size)
        assertTrue(variantResponse.isDefault)
        assertTrue(variantResponse.inStock)
        assertNull(variantResponse.priceOverride)
        assertEquals(listOf("https://example.com/black.jpg"), variantResponse.images)
        assertEquals(1, variantResponse.tierPricing.size)
        assertEquals(3, variantResponse.tierPricing[0].minQuantity)
        assertEquals(BigDecimal("109.99"), variantResponse.tierPricing[0].price)
    }

    // PIN-318: availability follows the summaries' rule, which the read model derives into inStock
    @Test
    fun `getProduct should report OUT_OF_STOCK when the product is out of stock`() {
        returning(product(slug = "gadget-pro", inStock = false, variants = listOf(variant(inStock = false))))

        val response = controller.getProduct(slug = "gadget-pro", sessionId = null, correlationId = null)

        assertEquals("OUT_OF_STOCK", response.body?.availability)
    }

    @Test
    fun `getProduct should report IN_STOCK when the product is in stock`() {
        returning(product(slug = "gadget-pro", inStock = true, variants = listOf(variant(inStock = false), variant(inStock = true))))

        val response = controller.getProduct(slug = "gadget-pro", sessionId = null, correlationId = null)

        assertEquals("IN_STOCK", response.body?.availability)
    }
}
