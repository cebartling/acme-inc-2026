import type { ProductSummary } from "@/services/api";
import { SearchResultCard } from "./SearchResultCard";

interface SearchResultsProps {
  query: string;
  totalResults: number;
  results: ProductSummary[];
  children?: React.ReactNode;
}

export function SearchResults({
  query,
  totalResults,
  results,
  children,
}: SearchResultsProps) {
  return (
    <div>
      <p
        data-testid="searchResultCount"
        aria-live="polite"
        className="mb-4 text-gray-300"
      >
        {totalResults} results for &lsquo;{query}&rsquo;
      </p>
      <div
        data-testid="searchResultsGrid"
        className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3"
      >
        {results.map((product) => (
          <SearchResultCard key={product.id} product={product} />
        ))}
      </div>
      {children}
    </div>
  );
}
