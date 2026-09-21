package com.acme.product.infrastructure.persistence

import com.acme.product.domain.Product
import com.acme.product.domain.ProductStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the category-browse queries added for US-0004-09 against a real PostgreSQL
 * instance.
 *
 * The controller and use-case tests mock the repository, so they cannot catch a malformed
 * native query or a JPQL typo. These queries back the search-unavailable fallback, so they
 * are verified here against a real database.
 *
 * The schema is generated from the entities rather than from Flyway: this service declares
 * `flyway-core` but not Spring Boot 4's `spring-boot-flyway` autoconfiguration module, so
 * migrations do not run inside a test slice. The queries under test touch only the products
 * table, which Hibernate maps faithfully.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CategoryBrowseRepositoryIntegrationTest {

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
            registry.add("spring.flyway.enabled") { "false" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }
        }
    }

    @Autowired
    private lateinit var repository: ProductRepository

    private fun product(
        slug: String,
        name: String,
        category: String?,
        status: ProductStatus = ProductStatus.PUBLISHED,
        createdAt: Instant = Instant.now()
    ) = Product(
        id = UUID.randomUUID(),
        slug = slug,
        name = name,
        price = BigDecimal("19.99"),
        status = status,
        category = category,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    @BeforeEach
    fun seed() {
        repository.deleteAll()
        val base = Instant.parse("2026-01-01T00:00:00Z")
        repository.saveAll(
            listOf(
                // Newest first by created_at, so browse order is electronics-3, -2, -1.
                product("electronics-1", "Widget One", "Electronics", createdAt = base),
                product("electronics-2", "Widget Two", "Electronics", createdAt = base.plusSeconds(60)),
                product("electronics-3", "Widget Three", "Electronics", createdAt = base.plusSeconds(120)),
                product("apparel-1", "Shirt", "Apparel", createdAt = base),
                // Must be excluded: archived, and null category.
                product("electronics-archived", "Old Widget", "Electronics", status = ProductStatus.ARCHIVED),
                product("uncategorized", "Mystery Item", null)
            )
        )
        repository.flush()
    }

    @Test
    fun `findDistinctCategories should return categories alphabetically with published counts`() {
        // When
        val categories = repository.findDistinctCategories()

        // Then
        assertEquals(listOf("Apparel", "Electronics"), categories.map { it.getCategory() })
        assertEquals(1L, categories[0].getCount())
        assertEquals(3L, categories[1].getCount(), "archived products must not be counted")
    }

    @Test
    fun `findDistinctCategories should exclude archived-only and null categories`() {
        // Given a category whose only product is archived
        repository.save(product("tools-archived", "Old Hammer", "Tools", status = ProductStatus.ARCHIVED))
        repository.flush()

        // When
        val names = repository.findDistinctCategories().map { it.getCategory() }

        // Then
        assertTrue(names.none { it == "Tools" }, "a category with no published products must not be listed")
        assertEquals(listOf("Apparel", "Electronics"), names)
    }

    @Test
    fun `findByCategory should return only published products in that category`() {
        // When
        val products = repository.findByCategory("Electronics", PageRequest.of(0, 100))

        // Then
        assertEquals(3, products.size)
        products.forEach {
            assertEquals("Electronics", it.category)
            assertEquals(ProductStatus.PUBLISHED, it.status)
        }
    }

    @Test
    fun `findByCategory should order newest first and paginate without overlap`() {
        // When
        val firstPage = repository.findByCategory("Electronics", PageRequest.of(0, 2))
        val secondPage = repository.findByCategory("Electronics", PageRequest.of(1, 2))

        // Then
        assertEquals(listOf("electronics-3", "electronics-2"), firstPage.map { it.slug })
        assertEquals(listOf("electronics-1"), secondPage.map { it.slug })
    }

    @Test
    fun `countByCategory should match the category facet count`() {
        // Given
        val electronics = repository.findDistinctCategories().first { it.getCategory() == "Electronics" }

        // When / Then
        assertEquals(electronics.getCount(), repository.countByCategory("Electronics"))
        assertEquals(3L, repository.countByCategory("Electronics"))
    }

    @Test
    fun `findByCategory should return empty for an unknown category`() {
        assertTrue(repository.findByCategory("NoSuchCategory", PageRequest.of(0, 24)).isEmpty())
        assertEquals(0L, repository.countByCategory("NoSuchCategory"))
    }

    @Test
    fun `every listed category should return products when browsed`() {
        // Guards against a mismatch between the native query's category value and the
        // JPQL lookup the fallback performs when a customer clicks a category.
        repository.findDistinctCategories().forEach { category ->
            val products = repository.findByCategory(category.getCategory(), PageRequest.of(0, 24))
            assertEquals(
                category.getCount(),
                products.size.toLong(),
                "category ${category.getCategory()} was listed but browse returned a different count"
            )
        }
    }
}
