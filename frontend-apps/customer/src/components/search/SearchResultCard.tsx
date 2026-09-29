import { Link } from "@tanstack/react-router";
import type { ProductSummary } from "@/services/api";

interface SearchResultCardProps {
  product: ProductSummary;
}

export function SearchResultCard({ product }: SearchResultCardProps) {
  return (
    <Link
      to="/products/$slug"
      params={{ slug: product.slug }}
      className="block rounded-lg bg-gray-800 p-4 shadow-md transition-colors hover:bg-gray-700"
      data-testid="searchResultCard"
    >
      {product.imageUrl && (
        <img
          src={product.imageUrl}
          alt=""
          data-testid="searchResultImage"
          className="mb-3 h-32 w-full rounded-md object-cover"
        />
      )}
      <h3 className="text-lg font-semibold text-white">{product.name}</h3>
      <p className="mt-1 text-xl font-bold text-cyan-400">
        ${product.price.toFixed(2)}
      </p>
      {/* US-0004-10 AC-06: badged, not hidden; text, not color alone */}
      {!product.inStock && (
        <span
          data-testid="outOfStockBadge"
          className="mt-2 mr-2 inline-block rounded-full bg-red-950 px-3 py-1 text-xs font-medium text-red-300"
        >
          Out of Stock
        </span>
      )}
      {product.category && (
        <span className="mt-2 inline-block rounded-full bg-gray-700 px-3 py-1 text-xs text-gray-300">
          {product.category}
        </span>
      )}
    </Link>
  );
}
