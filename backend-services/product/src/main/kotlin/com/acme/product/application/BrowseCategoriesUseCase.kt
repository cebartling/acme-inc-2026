package com.acme.product.application

import com.acme.product.domain.ProductSummary
import com.acme.product.infrastructure.projection.CatalogReadModelQueries
import org.springframework.stereotype.Service
import kotlin.math.ceil

/**
 * Category browsing, used as the fallback when product search is unavailable (US-0004-09).
 *
 * Reads categories and products straight from the catalog read model rather than going through
 * [SearchProductsUseCase], so this path stays available when the search path is failing.
 * Categories are a flat list: `category` is a plain field on a product, not a tree.
 */
@Service
class BrowseCategoriesUseCase(
    private val queries: CatalogReadModelQueries
) {
    data class Category(
        val name: String,
        val productCount: Long
    )

    data class CategoryProducts(
        val category: String,
        val products: List<ProductSummary>,
        val totalResults: Long,
        val page: Int,
        val pageSize: Int,
        val totalPages: Int
    )

    /**
     * Lists every category that has at least one published product, alphabetically.
     */
    fun listCategories(): List<Category> =
        queries.listCategories().map { Category(name = it.name, productCount = it.productCount) }

    /**
     * Lists published products in a category.
     *
     * @param category The category name.
     * @param page 1-based page number.
     * @param pageSize Number of products per page.
     */
    fun productsInCategory(
        category: String,
        page: Int = 1,
        pageSize: Int = 24
    ): CategoryProducts {
        require(page >= 1) { "page must be 1 or greater" }
        require(pageSize in 1..100) { "pageSize must be between 1 and 100" }

        val totalResults = queries.countByCategory(category)
        val products = queries.findByCategory(category, page, pageSize)

        return CategoryProducts(
            category = category,
            products = products,
            totalResults = totalResults,
            page = page,
            pageSize = pageSize,
            totalPages = if (totalResults == 0L) 0 else ceil(totalResults.toDouble() / pageSize).toInt()
        )
    }
}
