package com.acme.product.application

import com.acme.product.domain.ProductSummary
import com.acme.product.infrastructure.persistence.ProductRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import kotlin.math.ceil

/**
 * Category browsing, used as the fallback when product search is unavailable (US-0004-09).
 *
 * Reads categories and products straight from the products table rather than going through
 * [SearchProductsUseCase], so this path stays available when the full-text search path is
 * failing. Categories are a flat list: `category` is a plain column on products, not a tree.
 */
@Service
class BrowseCategoriesUseCase(
    private val repository: ProductRepository
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
        repository.findDistinctCategories().map {
            Category(name = it.getCategory(), productCount = it.getCount())
        }

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

        val totalResults = repository.countByCategory(category)
        val products = repository.findByCategory(category, PageRequest.of(page - 1, pageSize))

        return CategoryProducts(
            category = category,
            products = products.map { p ->
                ProductSummary(
                    id = p.id,
                    slug = p.slug,
                    name = p.name,
                    price = p.price,
                    category = p.category
                )
            },
            totalResults = totalResults,
            page = page,
            pageSize = pageSize,
            totalPages = if (totalResults == 0L) 0 else ceil(totalResults.toDouble() / pageSize).toInt()
        )
    }
}
