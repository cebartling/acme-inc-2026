package com.acme.product.api.v1

import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class SearchFiltersRequest(
    val categories: List<String> = emptyList(),
    @field:DecimalMin("0") val priceMin: BigDecimal? = null,
    @field:DecimalMin("0") val priceMax: BigDecimal? = null
)

data class SearchRequest(
    @field:NotBlank @field:Size(max = 200) val query: String,
    @field:Min(1) val page: Int = 1,
    @field:Min(1) @field:Max(100) val pageSize: Int = 24,
    val sort: String = "relevance",
    @field:Valid val filters: SearchFiltersRequest = SearchFiltersRequest()
)
