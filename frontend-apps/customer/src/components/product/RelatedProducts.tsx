import { SearchResultCard } from "@/components/search/SearchResultCard";
import type { ProductSummary } from "@/services/api";

interface RelatedProductsProps {
  products: ProductSummary[];
  /**
   * The viewed variant is out of stock: the service returns only in-stock related products,
   * so they are offered as alternatives (US-0004-10 AC-04).
   */
  alternatives?: boolean;
}

export function RelatedProducts({
  products,
  alternatives = false,
}: RelatedProductsProps) {
  if (products.length === 0) return null;

  return (
    <section className="mt-12" aria-labelledby="related-heading">
      <h2
        id="related-heading"
        className="mb-4 text-xl font-semibold text-white"
      >
        {alternatives ? "Alternatives you might like" : "Related Products"}
      </h2>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {products.map((product) => (
          <SearchResultCard key={product.id} product={product} />
        ))}
      </div>
    </section>
  );
}
