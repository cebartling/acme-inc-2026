package com.acme.product.infrastructure.projection

import com.acme.product.domain.ProductSummary
import org.bson.Document
import org.bson.types.Decimal128
import java.util.UUID

/** A product's card in a list: what [CatalogReadModel.VIEW] stores at the top level of its document. */
internal fun Document.toProductSummary() = ProductSummary(
    id = UUID.fromString(getString("_id")),
    slug = getString("slug"),
    name = getString("name"),
    price = get("price", Decimal128::class.java).bigDecimalValue(),
    category = getString("category"),
    inStock = getBoolean("inStock"),
    imageUrl = getString("imageUrl")
)
