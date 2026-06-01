package com.acme.product.domain

import java.util.UUID

class VariantNotFoundException(variantId: UUID) :
    RuntimeException("Variant not found: $variantId")
