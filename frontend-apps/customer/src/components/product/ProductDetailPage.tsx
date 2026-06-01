import type { ProductDetail } from "@/services/api";
import { useVariantSelection } from "@/hooks/useVariantSelection";
import { ProductAvailabilityBadge } from "./ProductAvailabilityBadge";
import { ProductBreadcrumb } from "./ProductBreadcrumb";
import { ProductPriceDisplay } from "./ProductPriceDisplay";
import { RelatedProducts } from "./RelatedProducts";
import { VariantSelector } from "./VariantSelector";

interface ProductDetailPageProps {
  product: ProductDetail;
}

function PriceSkeleton() {
  return (
    <div className="animate-pulse space-y-1">
      <div className="h-8 w-32 rounded bg-slate-700" />
    </div>
  );
}

function AvailabilitySkeleton() {
  return <div className="animate-pulse h-5 w-20 rounded bg-slate-700" />;
}

export function ProductDetailPage({ product }: ProductDetailPageProps) {
  const {
    selectedVariant,
    selectedVariantId,
    setSelectedVariantId,
    availability,
    price,
    isLoading,
    isError,
  } = useVariantSelection(product);

  const hasVariants = product.variants.length > 0;
  const effectiveAvailability =
    availability?.availability ?? product.availability;
  const isOutOfStock = effectiveAvailability === "OUT_OF_STOCK";

  const images = selectedVariant?.images ?? [];

  return (
    <div
      data-testid="productDetailPage"
      className="mx-auto max-w-5xl px-4 py-8"
    >
      <ProductBreadcrumb
        category={product.category}
        productName={product.name}
      />

      <div className="rounded-xl bg-slate-800 p-6 shadow-lg">
        {/* Image gallery */}
        {images.length > 0 && (
          <div
            className="mb-6 flex gap-3 overflow-x-auto pb-2"
            aria-label="Product images"
          >
            {images.map((url, idx) => (
              <img
                key={url}
                src={url}
                alt={`${product.name}${selectedVariant ? ` — ${selectedVariant.name}` : ""} image ${idx + 1}`}
                className="h-48 w-64 flex-shrink-0 rounded-lg object-cover"
              />
            ))}
          </div>
        )}

        <div className="mb-2">
          {product.category && (
            <span className="mb-3 inline-block rounded-full bg-slate-700 px-3 py-1 text-xs text-slate-300">
              {product.category}
            </span>
          )}
        </div>

        <h1
          data-testid="productName"
          className="mb-4 text-3xl font-bold text-white"
        >
          {product.name}
        </h1>

        {/* Variant selectors */}
        {hasVariants && (
          <div className="mb-6">
            <VariantSelector
              variants={product.variants}
              selectedVariantId={selectedVariantId}
              onSelect={setSelectedVariantId}
            />
          </div>
        )}

        {/* SKU */}
        {selectedVariant && (
          <p data-testid="variantSku" className="mb-4 text-xs text-slate-500">
            SKU: {selectedVariant.sku}
          </p>
        )}

        {/* Price and availability */}
        <div className="mb-4 flex flex-wrap items-start gap-4">
          {isLoading ? (
            <>
              <PriceSkeleton />
              <AvailabilitySkeleton />
            </>
          ) : isError ? (
            <p className="text-sm text-red-400">
              Could not load variant details. Please try again.
            </p>
          ) : (
            <>
              <ProductPriceDisplay
                price={price?.price ?? product.price}
                originalPrice={price?.originalPrice}
                tierPricing={price?.tierPricing}
              />
              <ProductAvailabilityBadge availability={effectiveAvailability} />
            </>
          )}
        </div>

        {/* Add to Cart */}
        <button
          type="button"
          disabled={isOutOfStock || isLoading || isError}
          className={[
            "mb-6 rounded-lg px-6 py-2.5 text-sm font-semibold transition-colors",
            isOutOfStock || isLoading || isError
              ? "cursor-not-allowed bg-slate-700 text-slate-500"
              : "bg-indigo-600 text-white hover:bg-indigo-500",
          ].join(" ")}
          aria-disabled={isOutOfStock || isLoading || isError}
        >
          {isOutOfStock ? "Out of Stock" : "Add to Cart"}
        </button>

        {product.description && (
          <div className="mb-6">
            <h2 className="mb-2 text-lg font-semibold text-slate-200">
              Description
            </h2>
            <p
              data-testid="productDescription"
              className="leading-relaxed text-slate-300"
            >
              {product.description}
            </p>
          </div>
        )}

        {product.tags.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {product.tags.map((tag) => (
              <span
                key={tag}
                className="rounded-full bg-slate-700 px-3 py-1 text-xs text-slate-400"
              >
                {tag}
              </span>
            ))}
          </div>
        )}
      </div>

      <RelatedProducts products={product.relatedProducts} />
    </div>
  );
}
