import type { ProductSummary } from "@/services/api";

interface SearchResultCardProps {
  product: ProductSummary;
}

export function SearchResultCard({ product }: SearchResultCardProps) {
  return (
    <div
      data-testid="searchResultCard"
      className="rounded-lg bg-gray-800 p-4 shadow-md"
    >
      <h3 className="text-lg font-semibold text-white">{product.name}</h3>
      <p className="mt-1 text-xl font-bold text-cyan-400">
        ${product.price.toFixed(2)}
      </p>
      {product.category && (
        <span className="mt-2 inline-block rounded-full bg-gray-700 px-3 py-1 text-xs text-gray-300">
          {product.category}
        </span>
      )}
    </div>
  );
}
