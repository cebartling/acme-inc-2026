package com.acme.product.infrastructure.persistence

import com.acme.product.domain.Product
import com.acme.product.domain.ProductStatus
import com.acme.product.domain.ProductVariant
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * US-0004-10 (PIN-273): the batch stock and image lookup behind the search, category and
 * related-product summaries. Checked against PostgreSQL because the use-case tests mock it and
 * it is a native query over three tables.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ProductStockRepositoryIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("acme_products_test")
            .withUsername("test")
            .withPassword("test")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var variantRepository: ProductVariantRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private fun product(): Product = productRepository.save(
        Product(
            id = UUID.randomUUID(),
            slug = "pin-273-${UUID.randomUUID()}",
            name = "PIN-273",
            price = BigDecimal("19.99"),
            status = ProductStatus.PUBLISHED
        )
    )

    private fun variant(
        product: Product,
        inStock: Boolean,
        isDefault: Boolean = false,
        createdAt: Instant = Instant.now()
    ): ProductVariant =
        variantRepository.save(
            ProductVariant(
                id = UUID.randomUUID(),
                product = product,
                sku = "PIN-273-${UUID.randomUUID()}",
                name = "V",
                isDefault = isDefault,
                inStock = inStock,
                createdAt = createdAt
            )
        )

    private fun image(variant: ProductVariant, url: String, displayOrder: Int) {
        variantRepository.flush()
        entityManager.createNativeQuery(
            "INSERT INTO product_variant_images (variant_id, url, display_order) VALUES (:v, :u, :o)"
        ).setParameter("v", variant.id).setParameter("u", url).setParameter("o", displayOrder).executeUpdate()
    }

    private fun stockOf(vararg products: Product) =
        productRepository.findStockSummaries(products.map { it.id }).associateBy { it.getProductId() }

    @Test
    fun `a product is in stock while any variant is, and out of stock when none is`() {
        val mixed = product().also { variant(it, inStock = false); variant(it, inStock = true) }
        val allOut = product().also { variant(it, inStock = false); variant(it, inStock = false) }

        val stock = stockOf(mixed, allOut)

        assertEquals(true, stock.getValue(mixed.id).getInStock())
        assertEquals(false, stock.getValue(allOut.id).getInStock())
    }

    @Test
    fun `a product without variants is in stock and has no image`() {
        val bare = product()
        productRepository.flush()

        val stock = stockOf(bare).getValue(bare.id)

        assertEquals(true, stock.getInStock())
        assertNull(stock.getImageUrl())
    }

    @Test
    fun `the image is the default variant's first image by display order`() {
        val withImages = product()
        val default = variant(withImages, inStock = true, isDefault = true)
        val other = variant(withImages, inStock = true)
        image(other, "https://img/other-0", 0)
        image(default, "https://img/default-1", 1)
        image(default, "https://img/default-0", 0)

        assertEquals("https://img/default-0", stockOf(withImages).getValue(withImages.id).getImageUrl())
    }

    @Test
    fun `without a default variant, the image is the first variant's, as on the product page`() {
        val noDefault = product()
        val later = variant(noDefault, inStock = true, createdAt = Instant.parse("2026-01-02T00:00:00Z"))
        val first = variant(noDefault, inStock = true, createdAt = Instant.parse("2026-01-01T00:00:00Z"))
        image(later, "https://img/later-0", 0)
        image(first, "https://img/first-0", 0)

        assertEquals("https://img/first-0", stockOf(noDefault).getValue(noDefault.id).getImageUrl())
    }
}
