package com.acme.product.api.v1

data class CategoryResponse(
    val name: String,
    val productCount: Long
)

data class CategoryListResponse(
    val categories: List<CategoryResponse>
)

data class CategoryProductsResponse(
    val category: String,
    val totalResults: Long,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int,
    val results: List<ProductSummaryResponse>
)
