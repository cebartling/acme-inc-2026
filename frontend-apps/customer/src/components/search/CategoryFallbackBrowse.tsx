import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { categoryApi } from "@/services/api";
import { SearchResultCard } from "./SearchResultCard";
import type {
  CategoryListResponse,
  CategoryProductsResponse,
} from "@/services/api";

/**
 * Category browsing shown when search is unavailable (AC-0004-09-02, AC-0004-09-07).
 *
 * Products are rendered with the same SearchResultCard used by normal results, so
 * cards and product links behave identically during an outage.
 */
export function CategoryFallbackBrowse() {
  const [selected, setSelected] = useState<string | null>(null);

  const {
    data: categoryData,
    isLoading: categoriesLoading,
    isError: categoriesError,
  } = useQuery<CategoryListResponse>({
    queryKey: ["categories"],
    queryFn: () => categoryApi.listCategories(),
  });

  const {
    data: productData,
    isLoading: productsLoading,
    isError: productsError,
  } = useQuery<CategoryProductsResponse>({
    queryKey: ["categoryProducts", selected],
    queryFn: () => categoryApi.productsInCategory(selected as string),
    enabled: selected !== null,
  });

  if (categoriesLoading) {
    return (
      <p className="mt-6 text-slate-400" data-testid="categoryFallbackLoading">
        Loading categories...
      </p>
    );
  }

  // Even the fallback can fail. Say so plainly rather than rendering an empty
  // region the customer cannot interpret.
  if (categoriesError || !categoryData) {
    return (
      <p className="mt-6 text-slate-400" data-testid="categoryFallbackError">
        Categories could not be loaded right now. Please try again shortly.
      </p>
    );
  }

  if (categoryData.categories.length === 0) {
    return (
      <p className="mt-6 text-slate-400" data-testid="categoryFallbackEmpty">
        No categories are available right now.
      </p>
    );
  }

  return (
    <div className="mt-6" data-testid="categoryFallbackBrowse">
      <h2 className="text-lg font-semibold text-white">Browse by category</h2>

      <ul className="mt-3 flex flex-wrap gap-2" data-testid="categoryList">
        {categoryData.categories.map((category) => {
          const isSelected = category.name === selected;
          return (
            <li key={category.name}>
              <button
                type="button"
                onClick={() => setSelected(category.name)}
                aria-pressed={isSelected}
                className={
                  isSelected
                    ? "rounded-full bg-cyan-600 px-4 py-2 text-sm font-medium text-white"
                    : "rounded-full bg-gray-800 px-4 py-2 text-sm text-gray-200 transition-colors hover:bg-gray-700"
                }
                data-testid="categoryListItem"
              >
                {category.name}
                <span className="ml-2 text-xs text-gray-400">
                  {category.productCount}
                </span>
              </button>
            </li>
          );
        })}
      </ul>

      {selected !== null && (
        <div className="mt-6" data-testid="categoryProducts">
          {productsLoading && (
            <p className="text-slate-400" data-testid="categoryProductsLoading">
              Loading {selected} products...
            </p>
          )}

          {productsError && (
            <p className="text-slate-400" data-testid="categoryProductsError">
              Products in {selected} could not be loaded right now.
            </p>
          )}

          {productData && productData.results.length === 0 && (
            <p className="text-slate-400" data-testid="categoryProductsEmpty">
              No products in {selected} right now.
            </p>
          )}

          {productData && productData.results.length > 0 && (
            <>
              <h3 className="text-md font-semibold text-white">
                {productData.totalResults} product
                {productData.totalResults === 1 ? "" : "s"} in {selected}
              </h3>
              <div
                className="mt-3 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3"
                data-testid="categoryProductsGrid"
              >
                {productData.results.map((product) => (
                  <SearchResultCard key={product.id} product={product} />
                ))}
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}
