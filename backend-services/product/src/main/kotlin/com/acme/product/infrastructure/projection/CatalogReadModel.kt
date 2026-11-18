package com.acme.product.infrastructure.projection

/**
 * Collections of the product catalog read model in `acme_query` (PIN-550).
 *
 * The four mirrors hold each captured table's rows as Debezium published them, keyed by the row's
 * id. [VIEW] holds one document per product with its variants, images and tier pricing embedded;
 * it is what the read endpoints query. The projector rebuilds a product's view document from the
 * mirrors on every change, so the order in which the four topics' messages arrive does not matter.
 */
object CatalogReadModel {
    const val PRODUCTS = "catalog_products"
    const val VARIANTS = "catalog_variants"
    const val IMAGES = "catalog_variant_images"
    const val TIER_PRICING = "catalog_tier_pricing"
    const val VIEW = "catalog_product_view"
}
