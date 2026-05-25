package com.acme.product.infrastructure.persistence

import com.acme.product.domain.Product
import com.acme.product.domain.ProductSummary
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.util.UUID

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
}
