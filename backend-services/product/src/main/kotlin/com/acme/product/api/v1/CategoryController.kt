package com.acme.product.api.v1

import com.acme.product.application.BrowseCategoriesUseCase
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for category browsing.
 *
 * Backs the search-unavailable fallback (US-0004-09): these endpoints deliberately do not
 * touch the full-text search path, so customers can keep discovering products while search
 * is down.
 */
@RestController
@RequestMapping("/api/v1/categories")
class CategoryController(
    private val browseCategoriesUseCase: BrowseCategoriesUseCase
) {

    /**
     * Lists every category with at least one published product.
     *
     * @return 200 OK with the category list.
     */
    @GetMapping
    fun listCategories(): ResponseEntity<CategoryListResponse> {
        val categories = browseCategoriesUseCase.listCategories()

        return ResponseEntity.ok(
            CategoryListResponse(
                categories = categories.map { CategoryResponse(name = it.name, productCount = it.productCount) }
            )
        )
    }

    /**
     * Lists published products within a category.
     *
     * @param name The category name.
     * @param page 1-based page number.
     * @param pageSize Number of products per page.
     * @return 200 OK with the paginated products.
     */
    @GetMapping("/{name}/products")
    fun productsInCategory(
        @PathVariable name: String,
        @RequestParam("page", defaultValue = "1") @Min(1) page: Int,
        @RequestParam("pageSize", defaultValue = "24") @Min(1) @Max(100) pageSize: Int
    ): ResponseEntity<CategoryProductsResponse> {
        val result = browseCategoriesUseCase.productsInCategory(name, page, pageSize)

        return ResponseEntity.ok(
            CategoryProductsResponse(
                category = result.category,
                totalResults = result.totalResults,
                page = result.page,
                pageSize = result.pageSize,
                totalPages = result.totalPages,
                results = result.products.map { p ->
                    ProductSummaryResponse(
                        id = p.id,
                        slug = p.slug,
                        name = p.name,
                        price = p.price,
                        category = p.category
                    )
                }
            )
        )
    }
}
