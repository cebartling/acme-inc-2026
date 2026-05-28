package com.acme.product.domain

class ProductNotFoundException(slug: String) : RuntimeException("Product not found: $slug")
