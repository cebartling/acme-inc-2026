package com.acme.product.infrastructure.persistence

import com.acme.product.domain.Product
import com.acme.product.domain.ProductStatus
import com.acme.product.domain.ProductVariant
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
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PIN-306: the price and availability lookups find a variant only while its product is
 * PUBLISHED, like every other product read. Checked against PostgreSQL because the controller
 * tests mock the repository and can't catch a wrong derived query.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class VariantLookupRepositoryIntegrationTest {

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

    private fun variantOf(status: ProductStatus): UUID {
        val product = productRepository.save(
            Product(
                id = UUID.randomUUID(),
                slug = "pin-306-${status.name.lowercase()}-${UUID.randomUUID()}",
                name = "PIN-306 ${status.name}",
                price = BigDecimal("19.99"),
                status = status
            )
        )
        val variant = variantRepository.save(
            ProductVariant(id = UUID.randomUUID(), product = product, sku = "PIN-306-${UUID.randomUUID()}", name = "Default")
        )
        variantRepository.flush()
        return variant.id
    }

    @Test
    fun `a published product's variant is found`() {
        val id = variantOf(ProductStatus.PUBLISHED)

        assertEquals(id, variantRepository.findByIdAndProductStatus(id, ProductStatus.PUBLISHED).orElseThrow().id)
    }

    @Test
    fun `an archived product's variant is not found`() {
        val id = variantOf(ProductStatus.ARCHIVED)

        assertTrue(variantRepository.findByIdAndProductStatus(id, ProductStatus.PUBLISHED).isEmpty)
    }
}
