package com.acme.product.infrastructure.persistence

import com.acme.product.domain.Product
import com.acme.product.domain.ProductSummary
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

/**
 * Projection interface for category facet counts.
 */
interface CategoryFacetProjection {
    fun getCategory(): String
    fun getCount(): Long
}

/**
 * Projection interface for the full-text search native query result set.
 */
interface ProductSearchProjection {
    fun getId(): UUID
    fun getSlug(): String
    fun getName(): String
    fun getPrice(): BigDecimal
    fun getCategory(): String?
}

/**
 * Projection interface for autocomplete product suggestions.
 */
interface AutocompleteProductProjection {
    fun getId(): UUID
    fun getName(): String
    fun getSlug(): String
}

/**
 * Spring Data JPA repository for [Product] entities.
 *
 * Includes native PostgreSQL full-text search queries using tsvector/tsquery.
 */
@Repository
interface ProductRepository : JpaRepository<Product, UUID> {

    /**
     * Full-text search using PostgreSQL tsquery ranked by ts_rank.
     * Excludes ARCHIVED products. Supports pagination.
     */
    @Query(
        value = """
            SELECT id, slug, name, price, category
            FROM products
            WHERE status = 'PUBLISHED'
              AND search_vector @@ plainto_tsquery('english', :query)
            ORDER BY ts_rank(search_vector, plainto_tsquery('english', :query)) DESC, id
            LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true
    )
    fun searchByRelevance(
        @Param("query") query: String,
        @Param("limit") limit: Int,
        @Param("offset") offset: Int
    ): List<ProductSearchProjection>

    /**
     * Full-text search ordered by price ascending.
     */
    @Query(
        value = """
            SELECT id, slug, name, price, category
            FROM products
            WHERE status = 'PUBLISHED'
              AND search_vector @@ plainto_tsquery('english', :query)
            ORDER BY price ASC, id
            LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true
    )
    fun searchByPriceAsc(
        @Param("query") query: String,
        @Param("limit") limit: Int,
        @Param("offset") offset: Int
    ): List<ProductSearchProjection>

    /**
     * Full-text search ordered by price descending.
     */
    @Query(
        value = """
            SELECT id, slug, name, price, category
            FROM products
            WHERE status = 'PUBLISHED'
              AND search_vector @@ plainto_tsquery('english', :query)
            ORDER BY price DESC, id
            LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true
    )
    fun searchByPriceDesc(
        @Param("query") query: String,
        @Param("limit") limit: Int,
        @Param("offset") offset: Int
    ): List<ProductSearchProjection>

    /**
     * Full-text search ordered by creation date descending (newest first).
     */
    @Query(
        value = """
            SELECT id, slug, name, price, category
            FROM products
            WHERE status = 'PUBLISHED'
              AND search_vector @@ plainto_tsquery('english', :query)
            ORDER BY created_at DESC, id
            LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true
    )
    fun searchByNewest(
        @Param("query") query: String,
        @Param("limit") limit: Int,
        @Param("offset") offset: Int
    ): List<ProductSearchProjection>

    /**
     * Count of matching PUBLISHED products for pagination.
     */
    @Query(
        value = """
            SELECT COUNT(*)
            FROM products
            WHERE status = 'PUBLISHED'
              AND search_vector @@ plainto_tsquery('english', :query)
        """,
        nativeQuery = true
    )
    fun countByQuery(@Param("query") query: String): Long

    /**
     * Spelling suggestion using pg_trgm similarity when zero results found.
     * Returns the most similar product name to the query.
     */
    @Query(
        value = """
            SELECT name
            FROM products
            WHERE status = 'PUBLISHED'
              AND similarity(name, :query) > 0.1
            ORDER BY similarity(name, :query) DESC
            LIMIT 1
        """,
        nativeQuery = true
    )
    fun findSpellingSuggestion(@Param("query") query: String): String?

    /**
     * Filtered full-text search ranked by ts_rank. Supports optional category and price range filters.
     * Pass null for categoryFilter to skip category filtering.
     */
    @Query(
        value = """
            SELECT id, slug, name, price, category
            FROM products
            WHERE status = 'PUBLISHED'
              AND search_vector @@ plainto_tsquery('english', :query)
              AND (:categoryFilter IS NULL OR category = ANY(STRING_TO_ARRAY(:categoryFilter, ',')))
              AND (:priceMin IS NULL OR price >= CAST(:priceMin AS NUMERIC))
              AND (:priceMax IS NULL OR price <= CAST(:priceMax AS NUMERIC))
            ORDER BY ts_rank(search_vector, plainto_tsquery('english', :query)) DESC, id
            LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true
    )
    fun searchByRelevanceFiltered(
        @Param("query") query: String,
        @Param("categoryFilter") categoryFilter: String?,
        @Param("priceMin") priceMin: BigDecimal?,
        @Param("priceMax") priceMax: BigDecimal?,
        @Param("limit") limit: Int,
        @Param("offset") offset: Int
    ): List<ProductSearchProjection>

    @Query(
        value = """
            SELECT id, slug, name, price, category
            FROM products
            WHERE status = 'PUBLISHED'
              AND search_vector @@ plainto_tsquery('english', :query)
              AND (:categoryFilter IS NULL OR category = ANY(STRING_TO_ARRAY(:categoryFilter, ',')))
              AND (:priceMin IS NULL OR price >= CAST(:priceMin AS NUMERIC))
              AND (:priceMax IS NULL OR price <= CAST(:priceMax AS NUMERIC))
            ORDER BY price ASC, id
            LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true
    )
    fun searchByPriceAscFiltered(
        @Param("query") query: String,
        @Param("categoryFilter") categoryFilter: String?,
        @Param("priceMin") priceMin: BigDecimal?,
        @Param("priceMax") priceMax: BigDecimal?,
        @Param("limit") limit: Int,
        @Param("offset") offset: Int
    ): List<ProductSearchProjection>

    @Query(
        value = """
            SELECT id, slug, name, price, category
            FROM products
            WHERE status = 'PUBLISHED'
              AND search_vector @@ plainto_tsquery('english', :query)
              AND (:categoryFilter IS NULL OR category = ANY(STRING_TO_ARRAY(:categoryFilter, ',')))
              AND (:priceMin IS NULL OR price >= CAST(:priceMin AS NUMERIC))
              AND (:priceMax IS NULL OR price <= CAST(:priceMax AS NUMERIC))
            ORDER BY price DESC, id
            LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true
    )
    fun searchByPriceDescFiltered(
        @Param("query") query: String,
        @Param("categoryFilter") categoryFilter: String?,
        @Param("priceMin") priceMin: BigDecimal?,
        @Param("priceMax") priceMax: BigDecimal?,
        @Param("limit") limit: Int,
        @Param("offset") offset: Int
    ): List<ProductSearchProjection>

    @Query(
        value = """
            SELECT id, slug, name, price, category
            FROM products
            WHERE status = 'PUBLISHED'
              AND search_vector @@ plainto_tsquery('english', :query)
              AND (:categoryFilter IS NULL OR category = ANY(STRING_TO_ARRAY(:categoryFilter, ',')))
              AND (:priceMin IS NULL OR price >= CAST(:priceMin AS NUMERIC))
              AND (:priceMax IS NULL OR price <= CAST(:priceMax AS NUMERIC))
            ORDER BY created_at DESC, id
            LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true
    )
    fun searchByNewestFiltered(
        @Param("query") query: String,
        @Param("categoryFilter") categoryFilter: String?,
        @Param("priceMin") priceMin: BigDecimal?,
        @Param("priceMax") priceMax: BigDecimal?,
        @Param("limit") limit: Int,
        @Param("offset") offset: Int
    ): List<ProductSearchProjection>

    /**
     * Count of matching products with optional category and price range filters.
     */
    @Query(
        value = """
            SELECT COUNT(*)
            FROM products
            WHERE status = 'PUBLISHED'
              AND search_vector @@ plainto_tsquery('english', :query)
              AND (:categoryFilter IS NULL OR category = ANY(STRING_TO_ARRAY(:categoryFilter, ',')))
              AND (:priceMin IS NULL OR price >= CAST(:priceMin AS NUMERIC))
              AND (:priceMax IS NULL OR price <= CAST(:priceMax AS NUMERIC))
        """,
        nativeQuery = true
    )
    fun countByQueryFiltered(
        @Param("query") query: String,
        @Param("categoryFilter") categoryFilter: String?,
        @Param("priceMin") priceMin: BigDecimal?,
        @Param("priceMax") priceMax: BigDecimal?
    ): Long

    /**
     * Category facet counts for a search query with optional price filter.
     * Category filter is intentionally excluded so all category options remain visible.
     */
    @Query(
        value = """
            SELECT category, COUNT(*) AS count
            FROM products
            WHERE status = 'PUBLISHED'
              AND category IS NOT NULL
              AND search_vector @@ plainto_tsquery('english', :query)
              AND (:priceMin IS NULL OR price >= CAST(:priceMin AS NUMERIC))
              AND (:priceMax IS NULL OR price <= CAST(:priceMax AS NUMERIC))
            GROUP BY category
            ORDER BY count DESC
        """,
        nativeQuery = true
    )
    fun getCategoryFacets(
        @Param("query") query: String,
        @Param("priceMin") priceMin: BigDecimal?,
        @Param("priceMax") priceMax: BigDecimal?
    ): List<CategoryFacetProjection>

    /**
     * Finds a published product by its URL slug.
     */
    fun findBySlug(slug: String): Optional<Product>

    /**
     * Finds published products in the same category, excluding the given product.
     * Used for the related products section on the product detail page.
     */
    @Query("SELECT p FROM Product p WHERE p.category = :category AND p.id <> :excludeId AND p.status = com.acme.product.domain.ProductStatus.PUBLISHED ORDER BY p.createdAt DESC")
    fun findRelatedProducts(
        @Param("category") category: String,
        @Param("excludeId") excludeId: UUID,
        pageable: Pageable
    ): List<Product>

    /**
     * Autocomplete: product names matching the query prefix using ILIKE.
     * Leverages the existing pg_trgm GIN index on name for performance.
     */
    @Query(
        value = """
            SELECT id, name, slug
            FROM products
            WHERE status = 'PUBLISHED'
              AND name ILIKE :prefix || '%' ESCAPE '\'
            ORDER BY similarity(name, :prefix) DESC, name
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun autocompleteProducts(
        @Param("prefix") prefix: String,
        @Param("limit") limit: Int
    ): List<AutocompleteProductProjection>

    /**
     * Autocomplete: distinct categories matching the query prefix using ILIKE.
     */
    @Query(
        value = """
            SELECT DISTINCT category
            FROM products
            WHERE status = 'PUBLISHED'
              AND category IS NOT NULL
              AND category ILIKE :prefix || '%' ESCAPE '\'
            ORDER BY category
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun autocompleteCategories(
        @Param("prefix") prefix: String,
        @Param("limit") limit: Int
    ): List<String>
}
