import type { ProductDetail } from "@/services/api";
import { ProductAvailabilityBadge } from "./ProductAvailabilityBadge";
import { ProductBreadcrumb } from "./ProductBreadcrumb";
import { ProductPriceDisplay } from "./ProductPriceDisplay";
import { RelatedProducts } from "./RelatedProducts";

interface ProductDetailPageProps {
  product: ProductDetail;
}

export function ProductDetailPage({ product }: ProductDetailPageProps) {
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

        <div className="mb-4 flex flex-wrap items-center gap-4">
          <ProductPriceDisplay price={product.price} />
          <ProductAvailabilityBadge availability={product.availability} />
        </div>

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
