package com.acme.product.api.v1

import java.util.UUID

data class VariantAvailabilityResponse(
    val variantId: UUID,
    val availability: String
)
